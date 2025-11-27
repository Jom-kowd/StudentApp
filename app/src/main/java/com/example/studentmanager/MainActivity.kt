package com.example.studentmanager

import androidx.compose.material.icons.automirrored.filled.Assignment
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// --- 1. THEME & COLORS ---
val DeepViolet = Color(0xFF4A148C)
val SoftViolet = Color(0xFF7C43BD)
val ElectricTeal = Color(0xFF009688)
val SoftTeal = Color(0xFF80CBC4)
val OffWhite = Color(0xFFF5F5F7)
val CardWhite = Color(0xFFFFFFFF)
val TextDark = Color(0xFF1A1A1A)
val TextGray = Color(0xFF757575)

// --- 2. MODEL (DATA CLASSES) ---
data class Assignment(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val deadline: String,
    val tag: String,
    val isCompleted: Boolean = false
)

data class BrainNote(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val colorIndex: Int = 0 // For varied sticky note colors
)

// --- 3. VIEWMODEL (LOGIC & PERSISTENCE) ---
class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("student_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // State using SnapshotStateList for automatic UI updates
    var assignments = mutableStateListOf<Assignment>()
        private set
    var notes = mutableStateListOf<BrainNote>()
        private set

    init {
        loadData()
    }

    // --- Actions ---
    fun addAssignment(title: String, deadline: String, tag: String) {
        assignments.add(0, Assignment(title = title, deadline = deadline, tag = tag))
        saveData()
    }

    fun toggleAssignment(assignment: Assignment) {
        val index = assignments.indexOfFirst { it.id == assignment.id }
        if (index != -1) {
            assignments[index] = assignments[index].copy(isCompleted = !assignment.isCompleted)
            saveData()
        }
    }

    fun deleteAssignment(assignment: Assignment) {
        assignments.remove(assignment)
        saveData()
    }

    fun addNote(content: String) {
        notes.add(0, BrainNote(content = content, colorIndex = (0..3).random()))
        saveData()
    }

    fun deleteNote(note: BrainNote) {
        notes.remove(note)
        saveData()
    }

    // --- Persistence Logic ---
    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("assignments", gson.toJson(assignments))
        editor.putString("notes", gson.toJson(notes))
        editor.apply()
    }

    private fun loadData() {
        val assignmentJson = prefs.getString("assignments", null)
        val noteJson = prefs.getString("notes", null)

        if (assignmentJson != null) {
            val type = object : TypeToken<List<Assignment>>() {}.type
            val savedList: List<Assignment> = gson.fromJson(assignmentJson, type)
            assignments.addAll(savedList)
        }
        if (noteJson != null) {
            val type = object : TypeToken<List<BrainNote>>() {}.type
            val savedNotes: List<BrainNote> = gson.fromJson(noteJson, type)
            notes.addAll(savedNotes)
        }
    }

    // Helper for Dashboard
    fun getProgress(): Float {
        if (assignments.isEmpty()) return 0f
        return assignments.count { it.isCompleted }.toFloat() / assignments.size.toFloat()
    }
}

// --- 4. UI (COMPOSABLES) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = DeepViolet,
                    secondary = ElectricTeal,
                    background = OffWhite,
                    surface = CardWhite
                )
            ) {
                StudentApp()
            }
        }
    }
}

@Composable
fun StudentApp(viewModel: StudentViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DeepViolet,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    // --- FIX IS HERE: Used AutoMirrored ---
                    icon = { Icon(Icons.AutoMirrored.Filled.Assignment, "Tasks") },
                    label = { Text("Assignments") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Lightbulb, "Brain Dump") },
                    label = { Text("Brain Dump") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OffWhite)
                .padding(padding)
        ) {
            // Header
            TopHeader()

            // Content Switcher
            if (selectedTab == 0) {
                DashboardSection(progress = viewModel.getProgress())
                AssignmentList(
                    assignments = viewModel.assignments,
                    onToggle = { viewModel.toggleAssignment(it) },
                    onDelete = { viewModel.deleteAssignment(it) }
                )
            } else {
                NoteGrid(
                    notes = viewModel.notes,
                    onDelete = { viewModel.deleteNote(it) }
                )
            }
        }

        // Add Dialog Logic
        if (showAddDialog) {
            if (selectedTab == 0) {
                AddAssignmentDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { t, d, tag ->
                        viewModel.addAssignment(t, d, tag)
                        showAddDialog = false
                    }
                )
            } else {
                AddNoteDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { content ->
                        viewModel.addNote(content)
                        showAddDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun TopHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Hello, Student",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "Let's be productive today.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.AccountCircle,
            contentDescription = "Profile",
            tint = DeepViolet,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun DashboardSection(progress: Float) {
    // Smooth animation for progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DeepViolet),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Your Progress", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", color = SoftTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = ElectricTeal,
                trackColor = SoftViolet
            )
        }
    }
}

@Composable
fun AssignmentList(
    assignments: List<Assignment>,
    onToggle: (Assignment) -> Unit,
    onDelete: (Assignment) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Tasks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(assignments, key = { it.id }) { item ->
            AssignmentCard(item, onToggle, onDelete)
        }
    }
}

@Composable
fun AssignmentCard(
    item: Assignment,
    onToggle: (Assignment) -> Unit,
    onDelete: (Assignment) -> Unit
) {
    // Determine colors based on state
    val cardColor = if (item.isCompleted) OffWhite else CardWhite
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCompleted) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom Checkbox
            FilledIconToggleButton(
                checked = item.isCompleted,
                onCheckedChange = { onToggle(item) },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = OffWhite,
                    contentColor = Color.Gray,
                    checkedContainerColor = ElectricTeal,
                    checkedContentColor = Color.White
                )
            ) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done")
                else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                    textDecoration = textDeco
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Badge(containerColor = SoftViolet.copy(alpha = 0.1f), contentColor = DeepViolet) {
                        Text(item.tag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.deadline, style = MaterialTheme.typography.labelMedium, color = TextGray)
                }
            }

            IconButton(onClick = { onDelete(item) }) {
                Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text(
                "Brain Dump",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(notes) { note ->
            val noteColors = listOf(
                Color(0xFFFFF9C4), // Light Yellow
                Color(0xFFE1BEE7), // Light Purple
                Color(0xFFB2DFDB), // Light Teal
                Color(0xFFFFCCBC)  // Light Orange
            )
            Card(
                colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]),
                modifier = Modifier.height(150.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { onDelete(note) },
                        modifier = Modifier.align(Alignment.End).size(24.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

// --- DIALOGS ---

@Composable
fun AddAssignmentDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("Homework") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text("Deadline (e.g., Tomorrow)") }
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag (e.g., Exam)") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotEmpty()) onConfirm(title, deadline, tag) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextGray) }
        }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick Thought") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("What's on your mind?") },
                minLines = 3,
                maxLines = 5
            )
        },
        confirmButton = {
            Button(
                onClick = { if (content.isNotEmpty()) onConfirm(content) },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Save Note") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextGray) }
        }
    )
}