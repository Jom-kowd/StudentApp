package com.example.studentmanager.data

import androidx.room.*
import com.example.studentmanager.Assignment
import com.example.studentmanager.BrainNote
import com.example.studentmanager.Subject
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {

    // ------------------------------------
    // 1. ASSIGNMENTS
    // ------------------------------------
    @Query("SELECT * FROM assignments WHERE isDeleted = 0 ORDER BY deadline ASC")
    fun getActiveAssignments(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE isDeleted = 1 ORDER BY deadline DESC")
    fun getTrashedAssignments(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignments WHERE isDeleted = 0 AND title LIKE '%' || :query || '%' ORDER BY deadline ASC")
    fun searchAssignments(query: String): Flow<List<Assignment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignment(assignment: Assignment)

    @Update
    suspend fun updateAssignment(assignment: Assignment)

    @Delete
    suspend fun deleteAssignment(assignment: Assignment)


    // ------------------------------------
    // 2. NOTES
    // ------------------------------------
    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun getAllNotes(): Flow<List<BrainNote>>

    @Insert
    suspend fun insertNote(note: BrainNote)

    @Delete
    suspend fun deleteNote(note: BrainNote)


    // ------------------------------------
    // 3. SUBJECTS (Updated)
    // ------------------------------------

    // Filter out deleted subjects (soft delete check)
    @Query("SELECT * FROM subjects WHERE isDeleted = 0")
    fun getAllSubjects(): Flow<List<Subject>>

    // NEW: Get subjects in the trash
    @Query("SELECT * FROM subjects WHERE isDeleted = 1")
    fun getTrashedSubjects(): Flow<List<Subject>>

    @Insert
    suspend fun insertSubject(subject: Subject)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Delete
    suspend fun deleteSubject(subject: Subject)
}