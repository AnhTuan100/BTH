package com.example.baith2.data.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.example.baith2.data.entity.ContactEntity
import com.example.baith2.data.entity.PhoneNumberEntity

data class ContactWithPhones(
    @Embedded val contact: ContactEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "contactId"
    )
    val phoneNumbers: List<PhoneNumberEntity>
)
