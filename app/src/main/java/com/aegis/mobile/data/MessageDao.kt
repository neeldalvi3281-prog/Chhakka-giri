package com.aegis.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<TacticalMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: TacticalMessage)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM messages WHERE status = 'SENT' AND type = 'ALERT_CRITICAL'")
    suspend fun getPendingSosMessages(): List<TacticalMessage>
    
    @Query("UPDATE messages SET status = 'RELAYED' WHERE id = :id")
    suspend fun markAsRelayed(id: String)
}
