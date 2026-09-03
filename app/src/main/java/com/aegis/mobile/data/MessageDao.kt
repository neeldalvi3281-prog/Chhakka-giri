package com.aegis.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<TacticalMessage>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): TacticalMessage?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: TacticalMessage): Long

    @Update
    suspend fun updateMessage(message: TacticalMessage)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("SELECT * FROM messages WHERE type = 'ALERT_CRITICAL' AND status != 'UPLOADED' ORDER BY timestamp ASC")
    suspend fun getPendingSosMessages(): List<TacticalMessage>
    
    @Query("UPDATE messages SET status = 'RELAYED' WHERE id = :id")
    suspend fun markAsRelayed(id: String)

    @Query("UPDATE messages SET status = 'UPLOADED' WHERE id IN (:ids)")
    suspend fun markAsUploaded(ids: List<String>)

    @Query("UPDATE messages SET status = 'UPLOADED' WHERE id = :id")
    suspend fun markSingleAsUploaded(id: String)
}
