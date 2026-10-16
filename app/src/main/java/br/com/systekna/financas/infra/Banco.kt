/* Infraestrutura: Room (SQLite) como adaptador da porta RepositorioLancamentos.
   Cada lançamento fica cifrado (AES-GCM 256, chave do cofre do PIN) na tabela registro: só o mês e a hora de criação
   ficam abertos, para a busca por mês e a trava de 2 dias. AAD = "local/lancamentos/<id>", como os registros do PWA:
   um texto cifrado não pode ser trocado de linha. Banco em databases/financas.db, na pasta privada do app. */
package br.com.systekna.financas.infra

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.systekna.financas.aplicacao.LancamentoSalvo
import br.com.systekna.financas.aplicacao.RepositorioLancamentos
import br.com.systekna.financas.aplicacao.Sessao
import br.com.systekna.financas.dominio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

@Entity(tableName = "registro", indices = [Index("mes")])
data class RegistroEntity(
    @PrimaryKey val id: String,
    val mes: String,
    val criadoEm: Long,
    val iv: String,
    val ct: String,
)

// Versão 1 (sem criptografia). Fica só para migrar o que existia: depois do primeiro desbloqueio a tabela esvazia.
@Entity(tableName = "lancamento", indices = [Index("mes")])
data class LancamentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tipo: String,
    val descricao: String,
    val valorCentavos: Long,
    val data: String,
    val mes: String,
    val categoria: String,
    val obs: String? = null,
    val criadoEm: Long = System.currentTimeMillis(),
)

@Dao
interface RegistroDao {
    @Query("SELECT * FROM registro WHERE mes = :mes") fun doMes(mes: String): Flow<List<RegistroEntity>>
    @Query("SELECT * FROM registro") suspend fun todos(): List<RegistroEntity>
    @Insert suspend fun inserir(r: RegistroEntity)
    @Insert suspend fun inserirTodos(r: List<RegistroEntity>)
    @Query("DELETE FROM registro WHERE id = :id") suspend fun excluir(id: String)
    @Query("DELETE FROM registro") suspend fun apagarTudo()
    @Transaction suspend fun substituir(r: List<RegistroEntity>) { apagarTudo(); inserirTodos(r) }

    @Query("SELECT * FROM lancamento") suspend fun antigos(): List<LancamentoEntity>
    @Query("DELETE FROM lancamento") suspend fun apagarAntigos()
    @Transaction suspend fun migrar(novos: List<RegistroEntity>) { inserirTodos(novos); apagarAntigos() }
}

private val MIGRACAO_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `registro` (`id` TEXT NOT NULL, `mes` TEXT NOT NULL, `criadoEm` INTEGER NOT NULL, `iv` TEXT NOT NULL, `ct` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_registro_mes` ON `registro` (`mes`)")
    }
}

@Database(entities = [RegistroEntity::class, LancamentoEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun registroDao(): RegistroDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "financas.db")
                    .addMigrations(MIGRACAO_1_2).build().also { instancia = it }
            }
    }
}

class RepositorioCifrado(private val dao: RegistroDao, private val sessao: Sessao) : RepositorioLancamentos {
    private fun chave() = sessao.chave ?: throw IllegalStateException("App bloqueado.")
    private fun aad(id: String) = CriptoCasa.aad("local", "lancamentos", id)

    private fun selar(id: String, l: Lancamento, criadoEm: Long): RegistroEntity {
        val caixa = CriptoCasa.selar(chave(), JsonMapa.paraJson(l.paraMapa()), aad(id))
        return RegistroEntity(id, Modelo.mesDe(l.data), criadoEm, caixa.iv, caixa.ct)
    }
    // Registro que não abre (chave errada ou adulterado) fica de fora em vez de derrubar a tela.
    private fun abrir(r: RegistroEntity): LancamentoSalvo? = runCatching {
        val m = JsonMapa.mapa(CriptoCasa.abrir(chave(), Caixa(iv = r.iv, ct = r.ct), aad(r.id)))
        LancamentoSalvo(r.id, Lancamento.de(Validacao.limpar("lancamento", m)), r.criadoEm)
    }.getOrNull()
    private fun ordenar(l: List<LancamentoSalvo>) = l.sortedWith(compareByDescending<LancamentoSalvo> { it.lancamento.data }.thenByDescending { it.criadoEm })

    override fun doMes(mes: String): Flow<List<LancamentoSalvo>> =
        dao.doMes(mes).map { lista -> if (sessao.chave == null) emptyList() else ordenar(lista.mapNotNull(::abrir)) }.flowOn(Dispatchers.Default)

    override suspend fun inserir(l: Lancamento) = dao.inserir(selar(UUID.randomUUID().toString(), l, System.currentTimeMillis()))
    override suspend fun excluir(id: String) = dao.excluir(id)
    override suspend fun todos(): List<LancamentoSalvo> = ordenar(dao.todos().mapNotNull(::abrir))
    override suspend fun substituirTudo(itens: List<BackupCifrado.Item>) =
        dao.substituir(itens.map { selar(UUID.randomUUID().toString(), it.lancamento, it.criadoEm) })

    // Lançamentos da versão sem criptografia: cifra e apaga os abertos (uma vez, depois do primeiro desbloqueio).
    suspend fun migrarAntigos() {
        val antigos = dao.antigos(); if (antigos.isEmpty()) return
        dao.migrar(antigos.map { e -> selar(UUID.randomUUID().toString(), Lancamento(e.tipo, e.descricao, e.valorCentavos, e.data, e.categoria, obs = e.obs), e.criadoEm) })
    }
}
