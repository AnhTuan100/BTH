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

    // ── Sync helpers ──────────────────────────────────────────────────────────

    /** Tìm liên hệ nội bộ theo systemContactId đã lưu */
    @Transaction
    @Query("SELECT * FROM contacts WHERE systemContactId = :systemId LIMIT 1")
    suspend fun getContactBySystemId(systemId: Long): ContactWithPhones?

    /** Lấy toàn bộ danh sách để so sánh khi sync ngược lại */
    @Transaction
    @Query("SELECT * FROM contacts")
    suspend fun getAllContactsSnapshot(): List<ContactWithPhones>

    /** Upsert (insert-or-replace) dùng khi import từ hệ thống */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity): Long

    /** Upsert nhiều số điện thoại cùng lúc */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhoneNumbers(phones: List<PhoneNumberEntity>)
}
