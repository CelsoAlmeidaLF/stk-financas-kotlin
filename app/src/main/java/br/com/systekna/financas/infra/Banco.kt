/* Infraestrutura: Room (SQLite) como adaptador da porta RepositorioLancamentos. */
package br.com.systekna.financas.infra

import android.content.Context
import androidx.room.*
import br.com.systekna.financas.aplicacao.LancamentoSalvo
import br.com.systekna.financas.aplicacao.RepositorioLancamentos
import br.com.systekna.financas.dominio.Lancamento
import br.com.systekna.financas.dominio.Modelo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Tabela. Valores em centavos e datas ISO, como no PWA. "mes" indexado para a busca por mês (igual ao Firestore).
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
interface LancamentoDao {
    @Query("SELECT * FROM lancamento WHERE mes = :mes ORDER BY data DESC, id DESC")
    fun doMes(mes: String): Flow<List<LancamentoEntity>>

    @Insert
    suspend fun inserir(l: LancamentoEntity)

    @Query("DELETE FROM lancamento WHERE id = :id")
    suspend fun excluir(id: Long)
}

@Database(entities = [LancamentoEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lancamentoDao(): LancamentoDao

    companion object {
        @Volatile private var instancia: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "financas.db").build().also { instancia = it }
            }
    }
}

class RepositorioRoom(private val dao: LancamentoDao) : RepositorioLancamentos {
    override fun doMes(mes: String): Flow<List<LancamentoSalvo>> = dao.doMes(mes).map { lista ->
        lista.map { e -> LancamentoSalvo(e.id, Lancamento(e.tipo, e.descricao, e.valorCentavos, e.data, e.categoria, obs = e.obs), e.criadoEm) }
    }
    override suspend fun inserir(l: Lancamento) = dao.inserir(
        LancamentoEntity(tipo = l.tipo, descricao = l.descricao, valorCentavos = l.valorCentavos, data = l.data, mes = Modelo.mesDe(l.data), categoria = l.categoria, obs = l.obs)
    )
    override suspend fun excluir(id: Long) = dao.excluir(id)
}
