package app.vpncheck.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConfigEntity::class, CheckResultEntity::class, ProviderEntity::class, SourceStatusEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun checkResultDao(): CheckResultDao
    abstract fun providerDao(): ProviderDao
    abstract fun sourceStatusDao(): SourceStatusDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vpncheck.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
