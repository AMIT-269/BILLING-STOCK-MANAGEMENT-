package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.JewellerSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface JewellerSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: JewellerSettings)

    @Query("SELECT * FROM jeweller_settings WHERE accountId = :accountId LIMIT 1")
    fun getSettingsFlow(accountId: String): Flow<JewellerSettings?>

    @Query("SELECT * FROM jeweller_settings WHERE accountId = :accountId LIMIT 1")
    suspend fun getSettingsDirect(accountId: String): JewellerSettings?

    @Query("SELECT * FROM jeweller_settings WHERE UPPER(TRIM(gstNumber)) = UPPER(TRIM(:gst)) LIMIT 1")
    suspend fun findSettingsByGst(gst: String): JewellerSettings?

    @Query("DELETE FROM jeweller_settings WHERE accountId = :accountId")
    suspend fun deleteSettingsByAccountId(accountId: String)
}
