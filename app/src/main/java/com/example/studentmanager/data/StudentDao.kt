package com.example.studentmanager.data

import androidx.room.*
import com.example.studentmanager.Assignment
import com.example.studentmanager.BrainNote
import com.example.studentmanager.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    // ------------------------------------
    // 1. ASSIGNMENTS (With Trash & Search)
    // ------------------------------------

    // Get only ACTIVE assignments (isDeleted = false/0)
    @Query("SELECT * FROM assignments WHERE isDeleted = 0 ORDER BY deadline ASC")
    fun getActiveAssignments(): Flow<List<Assignment>>

    // Get TRASHED assignments (isDeleted = true/1)
    @Query("SELECT * FROM assignments WHERE isDeleted = 1 ORDER BY deadline DESC")
    fun getTrashedAssignments(): Flow<List<Assignment>>

    // Search functionality (Filter by title AND ensure they are active)
    @Query("SELECT * FROM assignments WHERE isDeleted = 0 AND title LIKE '%' || :query || '%' ORDER BY deadline ASC")
    fun searchAssignments(query: String): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment)

    @Update
    suspend fun updateAssignment(assignment: Assignment)

    // This is the "Hard Delete" (removes from database entirely)
    @Delete
    suspend fun deleteAssignment(assignment: Assignment)


    // ------------------------------------
    // 2. NOTES (Standard)
    // ------------------------------------

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<BrainNote>>

    @Insert
    suspend fun insertNote(note: BrainNote)

    @Delete
    suspend fun deleteNote(note: BrainNote)


    // ------------------------------------
    // 3. SUBJECTS (Standard)
    // ------------------------------------

    @Query("SELECT * FROM subjects")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert
    suspend fun insertSubject(subject: Subject)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)
}