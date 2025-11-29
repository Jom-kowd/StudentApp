package com.example.studentmanager.data

import androidx.room.*
import com.example.studentmanager.Assignment // Import your classes
import com.example.studentmanager.BrainNote
import com.example.studentmanager.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    // --- ASSIGNMENTS ---
    @Query("SELECT * FROM assignments ORDER BY deadline ASC")
    fun getAllAssignments(): Flow<List<Assignment>> // Flow updates UI automatically!

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment)

    @Delete
    suspend fun deleteAssignment(assignment: Assignment)

    @Update
    suspend fun updateAssignment(assignment: Assignment)

    // --- NOTES ---
    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<BrainNote>>

    @Insert
    suspend fun insertNote(note: BrainNote)

    @Delete
    suspend fun deleteNote(note: BrainNote)

    // --- SUBJECTS ---
    @Query("SELECT * FROM subjects")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert
    suspend fun insertSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)

    @Update
    suspend fun updateSubject(subject: Subject)
}