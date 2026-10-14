package com.example.baith2.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.baith2.data.dao.ContactDao
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.MessageEntity
import com.example.baith2.data.entity.PhoneNumberEntity

@Database(
    entities = [ContactEntity::class, PhoneNumberEntity::class, MessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ContactDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile
        private var INSTANCE: ContactDatabase? = null

        fun getDatabase(context: Context): ContactDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ContactDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ContactDatabase::class.java,
                "contact_database"
            )
                .fallbackToDestructiveMigration(true)
                .build()
        }
    }
}
