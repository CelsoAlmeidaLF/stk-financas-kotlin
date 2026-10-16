/* Aplicação + repositório cifrado no JVM: fluxo do PIN, bloqueio, recuperação, registros cifrados e backup.
   O DAO é falso (memória); a criptografia é a de verdade (iterações baixas para o teste ser rápido). */
package br.com.systekna.financas

import br.com.systekna.financas.aplicacao.*
import br.com.systekna.financas.dominio.*
import br.com.systekna.financas.infra.LancamentoEntity
import br.com.systekna.financas.infra.RegistroDao
import br.com.systekna.financas.infra.RegistroEntity
import br.com.systekna.financas.infra.RepositorioCifrado
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.*

private const val ITER = 1_000

class ArmazemMemoria : ArmazemCofre {
    var cofre: Cofre? = null; var e = Erros()
    override fun ler() = cofre
    override fun gravar(c: Cofre) { cofre = c }
    override fun erros() = e
    override fun gravarErros(e: Erros) { this.e = e }
}

class BioFalsa(var temNoAparelho: Boolean = true) : Biometria {
    var guardada: String? = null; var recusou = false
    var resposta: () -> Boolean = { true }       // true = digital aceita; false = cancelou
    var invalidada = false
    override fun disponivel() = temNoAparelho
    override fun ativa() = guardada != null
    override fun recusada() = recusou
    override fun recusar() { recusou = true }
    override suspend fun ativar(dek: String) = resposta().also { if (it) guardada = dek }
    override suspend fun abrir(): String? { if (invalidada) throw BiometriaInvalidada(); return if (resposta()) guardada else null }
    override fun desativar() { guardada = null }
}

class DaoMemoria : RegistroDao {
    val linhas = MutableStateFlow<Map<String, RegistroEntity>>(emptyMap())
    val antigosLista = mutableListOf<LancamentoEntity>()
    override fun doMes(mes: String) = linhas.map { m -> m.values.filter { it.mes == mes } }
    override suspend fun todos() = linhas.value.values.toList()
    override suspend fun inserir(r: RegistroEntity) { linhas.update { it + (r.id to r) } }
    override suspend fun inserirTodos(r: List<RegistroEntity>) { linhas.update { m -> m + r.associateBy { it.id } } }
    override suspend fun excluir(id: String) { linhas.update { it - id } }
    override suspend fun apagarTudo() { linhas.value = emptyMap() }
    override suspend fun antigos() = antigosLista.toList()
    override suspend fun apagarAntigos() { antigosLista.clear() }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AcessoEBackupTest {
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // Espera o PBKDF2 (roda em Dispatchers.Default) terminar.
    private fun AcessoViewModel.aguardar(): EstadoAcesso = runBlocking {
        withTimeout(10_000) { estado.first { it != EstadoAcesso.Verificando && it != EstadoAcesso.Carregando } }
    }

    @Test fun criarPinMostraCodigoELibera() {
        val arm = ArmazemMemoria(); val vm = AcessoViewModel(arm, BioFalsa(temNoAparelho = false), ITER)
        assertIs<EstadoAcesso.CriarPin>(vm.aguardar())
        vm.pinCriado("123456"); assertEquals("123456", (vm.aguardar() as EstadoAcesso.CriarPin).confirmar)
        vm.pinCriado("654321"); assertNotNull((vm.aguardar() as EstadoAcesso.CriarPin).erro) // não coincidem: recomeça
        vm.pinCriado("123456"); vm.pinCriado("123456")
        val codigo = assertIs<EstadoAcesso.MostrarCodigo>(vm.aguardar()).codigo
        assertEquals(32, codigo.replace("-", "").length)
        assertNotNull(vm.sessao.chave); assertNotNull(arm.cofre)
        vm.codigoGuardado(); assertEquals(EstadoAcesso.Liberado, vm.aguardar())
        vm.bloquear(); assertNull(vm.sessao.chave); assertIs<EstadoAcesso.Desbloquear>(vm.aguardar())
    }

    @Test fun pinErradoContaErrosEEsperaDepoisDeCinco() {
        val arm = ArmazemMemoria().apply { cofre = CofrePin.criar("111111", ITER).cofre }
        val vm = AcessoViewModel(arm, BioFalsa(temNoAparelho = false), ITER)
        assertIs<EstadoAcesso.Desbloquear>(vm.aguardar())
        repeat(4) { vm.pinDigitado("999999"); assertEquals(0, (vm.aguardar() as EstadoAcesso.Desbloquear).esperaAte) }
        vm.pinDigitado("999999")
        val bloq = vm.aguardar() as EstadoAcesso.Desbloquear
        assertTrue(bloq.esperaAte > System.currentTimeMillis()); assertEquals(5, arm.e.seguidos)
        vm.pinDigitado("111111"); assertSame(bloq, vm.aguardar()) // durante a espera nem tenta
        // Passada a espera, o PIN certo abre e zera o contador.
        arm.e = arm.e.copy(ultimoEm = 0); vm.voltarAoPin(); vm.pinDigitado("111111")
        assertEquals(EstadoAcesso.Liberado, vm.aguardar()); assertEquals(0, arm.e.seguidos)
    }

    @Test fun esqueciPinComCodigoTrocaOPinEMantemOsDados() {
        val novo = CofrePin.criar("111111", ITER)
        val arm = ArmazemMemoria().apply { cofre = novo.cofre }
        val vm = AcessoViewModel(arm, BioFalsa(temNoAparelho = false), ITER); vm.aguardar()
        vm.esqueciPin(); vm.codigoDigitado("AAAA-BBBB"); assertNotNull((vm.aguardar() as EstadoAcesso.Recuperar).erro)
        vm.codigoDigitado(novo.codigo); assertIs<EstadoAcesso.NovoPin>(vm.aguardar())
        vm.novoPinDigitado("222222"); vm.novoPinDigitado("222222")
        assertEquals(EstadoAcesso.Liberado, vm.aguardar())
        assertEquals(novo.dek, CofrePin.abrirComPin(arm.cofre!!, "222222")) // mesma chave: os dados continuam legíveis
        assertFailsWith<ErroCripto> { CofrePin.abrirComPin(arm.cofre!!, "111111") }
    }

    private fun sessaoAberta() = Sessao().apply { abrir(CofrePin.criar("123456", ITER).dek) }
    private val pao = Lancamento("despesa", "Pão de açúcar", 4590, "2026-10-07", "Mercado")

    @Test fun repositorioGravaCifradoEAbreComAChave() = runBlocking {
        val dao = DaoMemoria(); val sessao = sessaoAberta(); val repo = RepositorioCifrado(dao, sessao)
        repo.inserir(pao); repo.inserir(pao.copy(descricao = "Feira", data = "2026-10-09"))
        val linha = dao.linhas.value.values.first()
        assertEquals("2026-10", linha.mes); assertFalse(linha.ct.contains("Pão"))
        assertEquals(listOf("Feira", "Pão de açúcar"), repo.doMes("2026-10").first().map { it.lancamento.descricao })
        // Texto cifrado trocado de linha não abre (AAD = id): o registro some em vez de mostrar dado errado.
        val (a, b) = dao.linhas.value.values.toList()
        dao.linhas.value = mapOf(a.id to a.copy(iv = b.iv, ct = b.ct), b.id to b)
        assertEquals(1, repo.todos().size)
        sessao.fechar(); assertEquals(emptyList(), repo.doMes("2026-10").first())
    }

    @Test fun migraLancamentosAntigosSemCriptografia() = runBlocking {
        val dao = DaoMemoria().apply { antigosLista += LancamentoEntity(1, "entrada", "Salário", 500_000, "2026-10-05", "2026-10", "Salário", criadoEm = 42) }
        val repo = RepositorioCifrado(dao, sessaoAberta())
        repo.migrarAntigos()
        assertTrue(dao.antigosLista.isEmpty())
        val s = repo.todos().single(); assertEquals("Salário", s.lancamento.descricao); assertEquals(42, s.criadoEm)
    }

    @Test fun exportaERestauraEmOutroAparelhoComOutraChave() = runBlocking {
        val origem = LancamentosViewModel(RepositorioCifrado(DaoMemoria(), sessaoAberta()).also { it.inserir(pao) })
        val arquivo = origem.exportar("senha-do-backup", ITER)
        assertFalse(arquivo.contains("Pão"))
        val destinoDao = DaoMemoria().apply { linhas.value = mapOf("x" to RegistroEntity("x", "2026-01", 1, "aaa", "bbb")) }
        val destino = LancamentosViewModel(RepositorioCifrado(destinoDao, sessaoAberta()))
        assertFailsWith<ErroCripto> { destino.restaurar(arquivo, "senha-errada") }
        assertEquals(1, destinoDao.linhas.value.size) // senha errada não mexe em nada
        assertEquals(1, destino.restaurar(arquivo, "senha-do-backup"))
        val r = destinoDao.linhas.value.values.single()
        assertEquals("2026-10", r.mes)
        assertEquals(listOf(pao), destino.exportar("outra-senha", ITER).let { BackupCifrado.abrir(it, "outra-senha").map { i -> i.lancamento } })
    }

    @Test fun ofereceBiometriaDepoisDoPinEAbreComElaNaProximaVez() {
        val arm = ArmazemMemoria(); val bio = BioFalsa(); val vm = AcessoViewModel(arm, bio, ITER)
        vm.aguardar(); vm.pinCriado("123456"); vm.pinCriado("123456")
        assertIs<EstadoAcesso.MostrarCodigo>(vm.aguardar()); vm.codigoGuardado()
        assertEquals(EstadoAcesso.OferecerBiometria, vm.aguardar())
        var msg: String? = "x"; vm.ativarBiometria { msg = it }
        assertEquals(EstadoAcesso.Liberado, vm.aguardar()); assertNull(msg)
        assertEquals(vm.sessao.dek(), bio.guardada)
        val dek = vm.sessao.dek()
        vm.bloquear()
        val tela = assertIs<EstadoAcesso.Desbloquear>(vm.aguardar()); assertTrue(tela.biometria)
        vm.usarBiometria(); assertEquals(EstadoAcesso.Liberado, vm.aguardar()); assertEquals(dek, vm.sessao.dek())
    }

    @Test fun cancelarBiometriaFicaNoPinEDigitalNovaDesativa() {
        val n = CofrePin.criar("123456", ITER)
        val arm = ArmazemMemoria().apply { cofre = n.cofre }
        val bio = BioFalsa().apply { guardada = n.dek; resposta = { false } }
        val vm = AcessoViewModel(arm, bio, ITER)
        val tela = assertIs<EstadoAcesso.Desbloquear>(vm.aguardar())
        vm.usarBiometria(); assertSame(tela, vm.aguardar()); assertNull(vm.sessao.chave) // cancelou: continua no PIN
        bio.invalidada = true; vm.usarBiometria()
        val depois = assertIs<EstadoAcesso.Desbloquear>(vm.aguardar())
        assertNotNull(depois.erro); assertFalse(depois.biometria); assertNull(bio.guardada)
        vm.pinDigitado("123456"); assertEquals(EstadoAcesso.OferecerBiometria, vm.aguardar()) // oferece de novo
        vm.recusarBiometria(); assertEquals(EstadoAcesso.Liberado, vm.aguardar()); assertTrue(bio.recusou)
    }

    @Test fun semBiometriaNoAparelhoNaoOferece() {
        val vm = AcessoViewModel(ArmazemMemoria(), BioFalsa(temNoAparelho = false), ITER)
        vm.aguardar(); vm.pinCriado("123456"); vm.pinCriado("123456"); vm.aguardar(); vm.codigoGuardado()
        assertEquals(EstadoAcesso.Liberado, vm.aguardar())
    }
}
