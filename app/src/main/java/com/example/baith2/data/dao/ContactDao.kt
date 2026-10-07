package com.example.baith2.data.dao

import androidx.room.*
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.MessageEntity
import com.example.baith2.data.entity.PhoneNumberEntity
import com.example.baith2.data.relation.ContactWithPhones
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Transaction
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContactsWithPhones(): Flow<List<ContactWithPhones>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactWithPhones(contactId: Long): ContactWithPhones?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertContact(contact: ContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhoneNumber(phone: PhoneNumberEntity)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Query("DELETE FROM phone_numbers WHERE contactId = :contactId")
    suspend fun deletePhonesByContactId(contactId: Long)

    @Query("DELETE FROM contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE contactId = :contactId ORDER BY timestamp DESC")
    fun getMessagesByContactId(contactId: Long): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)
}
