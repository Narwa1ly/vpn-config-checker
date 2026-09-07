package app.vpncheck.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    @Transaction
    @Query("SELECT * FROM configs ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ConfigWithResults>>

    @Query("SELECT * FROM configs")
    suspend fun all(): List<ConfigEntity>

    @Query("SELECT * FROM configs WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<ConfigEntity>

    @Query("SELECT id FROM configs")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(items: List<ConfigEntity>)

    @Query("UPDATE configs SET lastSeenAt = :ts, name = :name, sourceIds = :sourceIds WHERE id = :id")
    suspend fun touch(id: String, ts: Long, name: String, sourceIds: String)

    @Query("UPDATE configs SET sourceIds = :sourceIds WHERE id = :id")
    suspend fun updateSources(id: String, sourceIds: String)

    @Query("DELETE FROM configs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM configs")
    suspend fun deleteAll()

    @Query("DELETE FROM configs WHERE lastSeenAt < :olderThan")
    suspend fun pruneOlderThan(olderThan: Long)

    @Query("SELECT COUNT(*) FROM configs")
    suspend fun count(): Int
}

@Dao
interface CheckResultDao {
    @Upsert
    suspend fun upsert(result: CheckResultEntity)

    @Query("SELECT * FROM check_results WHERE configId = :configId")
    suspend fun forConfig(configId: String): List<CheckResultEntity>

    @Query("SELECT * FROM check_results WHERE networkType = :networkType")
    suspend fun byType(networkType: String): List<CheckResultEntity>

    @Query("SELECT DISTINCT configId FROM check_results WHERE ok = 1")
    suspend fun okConfigIds(): List<String>

    @Query("DELETE FROM check_results")
    suspend fun deleteAll()

    @Query("DELETE FROM check_results WHERE networkType = :networkType")
    suspend fun clear(networkType: String)

    @Query("DELETE FROM check_results WHERE configId NOT IN (SELECT id FROM configs)")
    suspend fun pruneOrphans()
}

@Dao
interface ProviderDao {
    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Query("SELECT * FROM providers WHERE ip = :ip")
    suspend fun byIp(ip: String): ProviderEntity?

    @Query("SELECT * FROM providers")
    fun observeAll(): Flow<List<ProviderEntity>>
}

@Dao
interface SourceStatusDao {
    @Upsert
    suspend fun upsert(status: SourceStatusEntity)

    @Query("SELECT * FROM source_status WHERE sourceId = :sourceId")
    suspend fun byId(sourceId: String): SourceStatusEntity?

    @Query("SELECT * FROM source_status")
    fun observeAll(): Flow<List<SourceStatusEntity>>

    @Query("DELETE FROM source_status")
    suspend fun deleteAll()
}
