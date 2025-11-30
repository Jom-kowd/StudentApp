package com.example.studentmanager

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.studentmanager.data.StudentDatabase // Make sure this matches your package structure
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest

// --- 1. THEME & COLORS ---
val DeepViolet = Color(0xFF4A148C)
val SoftViolet = Color(0xFF7C43BD)
val ElectricTeal = Color(0xFF009688)
val SoftTeal = Color(0xFF80CBC4)
val OffWhite = Color(0xFFF5F5F7)
val CardWhite = Color(0xFFFFFFFF)
val TextDark = Color(0xFF1A1A1A)
val TextGray = Color(0xFF757575)
val Gold = Color(0xFFFFD700)
val WarningRed = Color(0xFFD32F2F)

// Schedule Block Colors (Pastels)
val ScheduleColors = listOf(
    Color(0xFFE57373), // Red
    Color(0xFFBA68C8), // Purple
    Color(0xFF64B5F6), // Blue
    Color(0xFF4DB6AC), // Teal
    Color(0xFFFFB74D), // Orange
    Color(0xFFAED581)  // Green
)

// NEW: Task Card Colors (Pastel versions for background)
val TaskColors = listOf(
    Color(0xFFFFFFFF), // 0: White (Default)
    Color(0xFFFFCDD2), // 1: Red
    Color(0xFFE1BEE7), // 2: Purple
    Color(0xFFBBDEFB), // 3: Blue
    Color(0xFFC8E6C9), // 4: Green
    Color(0xFFFFF9C4), // 5: Yellow
    Color(0xFFFFE0B2)  // 6: Orange
)

// PH Grading Colors
val GradeA = Color(0xFF4CAF50)
val GradeB = Color(0xFF8BC34A)
val GradeC = Color(0xFFFFC107)
val GradeD = Color(0xFFFF9800)
val GradeF = Color(0xFFF44336)

val CatActivity = Color(0xFF42A5F5)
val CatQuiz = Color(0xFFAB47BC)
val CatExam = Color(0xFFEF5350)
val CatThesis = Color(0xFFFFA726)
val CatOrg = Color(0xFF66BB6A)

// --- 2. MODEL (DATA CLASSES) ---

data class UserProfile(
    val name: String = "Student",
    val school: String = "Android Academy",
    val bio: String = "Coding my future.",
    val avatarId: Int = 0,
    val imageUri: String? = null,
    val category: String = "Computer Science"
)

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val deadline: Long,
    val category: String,
    val isCompleted: Boolean = false,
    val isDeleted: Boolean = false,
    // NEW FIELDS FOR CUSTOMIZATION
    val colorIndex: Int = 0,    // Background color
    val isBold: Boolean = false, // Bold Title
    val isLarge: Boolean = false // Large Text
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(deadline))
    }
    fun isUrgent(): Boolean {
        val now = System.currentTimeMillis()
        val diff = deadline - now
        return diff in 0..(24 * 60 * 60 * 1000) && !isCompleted
    }
}

@Entity(tableName = "notes")
data class BrainNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val colorIndex: Int = 0
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    val units: Int,
    val days: List<String>,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val colorIndex: Int = 0,
    val grade: Double? = null,
    val yearLevel: String,
    val semester: String
) {
    val scheduleString: String
        get() {
            val dayStr = days.joinToString("")
            val startStr = String.format("%02d:%02d", if(startHour==0 || startHour==12) 12 else startHour%12, startMinute)
            val startAmPm = if(startHour < 12) "AM" else "PM"
            val endStr = String.format("%02d:%02d", if(endHour==0 || endHour==12) 12 else endHour%12, endMinute)
            val endAmPm = if(endHour < 12) "AM" else "PM"
            return "$dayStr $startStr$startAmPm - $endStr$endAmPm"
        }
}

// --- 3. NOTIFICATION LOGIC ---
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("TITLE") ?: "Task Due!"
        val message = intent.getStringExtra("MESSAGE") ?: "You have a deadline coming up."
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "student_tasks_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Task Deadlines", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

// --- 4. VIEWMODEL (LOGIC) ---

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = StudentDatabase.getDatabase(application).studentDao()
    private val prefs = application.getSharedPreferences("student_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // --- NEW STATES FOR SEARCH & TRASH ---
    var searchQuery by mutableStateOf("")
    var filterType by mutableStateOf("All") // "All", "Urgent", "Completed"
    var showTrashBin by mutableStateOf(false) // Toggles the Trash Screen

    // Separate lists for Active Tasks vs Trash Bin
    var assignments = mutableStateListOf<Assignment>()
    var trashedAssignments = mutableStateListOf<Assignment>()

    // Standard lists for other features
    var notes = mutableStateListOf<BrainNote>()
    var subjects = mutableStateListOf<Subject>()

    // User Profile
    var userProfile by mutableStateOf(UserProfile())
        private set

    // UI States (Timer & Subject Filters)
    var selectedYear by mutableStateOf("1st Year")
    var selectedSem by mutableStateOf("1st Sem")
    var timerDuration by mutableLongStateOf(25 * 60 * 1000L)
    var timeLeft by mutableLongStateOf(25 * 60 * 1000L)
    var isTimerRunning by mutableStateOf(false)
    private var timerJob: Job? = null

    init {
        // 1. Observe ACTIVE assignments (Handles Search automatically)
        viewModelScope.launch {
            // "snapshotFlow" watches 'searchQuery'. Whenever it changes, this block re-runs.
            snapshotFlow { searchQuery }.collectLatest { query ->
                val flow = if (query.isEmpty()) dao.getActiveAssignments() else dao.searchAssignments(query)
                flow.collect { list ->
                    assignments.clear()
                    assignments.addAll(list)
                }
            }
        }

        // 2. Observe TRASH assignments
        viewModelScope.launch {
            dao.getTrashedAssignments().collect { list ->
                trashedAssignments.clear()
                trashedAssignments.addAll(list)
            }
        }

        // 3. Observe Notes
        viewModelScope.launch {
            dao.getAllNotes().collect { list ->
                notes.clear()
                notes.addAll(list)
            }
        }

        // 4. Observe Subjects
        viewModelScope.launch {
            dao.getAllSubjects().collect { list ->
                subjects.clear()
                subjects.addAll(list)
            }
        }
        loadProfile()
    }

    // --- ASSIGNMENT ACTIONS (Enhanced) ---

    // Create New - UPDATED to include color and style
    fun addAssignment(title: String, deadline: Long, category: String, colorIndex: Int, isBold: Boolean, isLarge: Boolean) {
        viewModelScope.launch {
            val newAssignment = Assignment(
                title = title,
                deadline = deadline,
                category = category,
                colorIndex = colorIndex,
                isBold = isBold,
                isLarge = isLarge
            )
            dao.insertAssignment(newAssignment)
            scheduleNotification(newAssignment)
        }
    }

    // Update (Rename or Change Date)
    fun updateAssignment(assignment: Assignment) {
        viewModelScope.launch { dao.updateAssignment(assignment) }
    }

    // Mark Complete/Incomplete
    fun toggleAssignment(assignment: Assignment) {
        viewModelScope.launch {
            dao.updateAssignment(assignment.copy(isCompleted = !assignment.isCompleted))
        }
    }

    // Soft Delete (Move to Trash)
    fun moveAssignmentToTrash(assignment: Assignment) {
        viewModelScope.launch { dao.updateAssignment(assignment.copy(isDeleted = true)) }
    }

    // Restore from Trash
    fun restoreAssignment(assignment: Assignment) {
        viewModelScope.launch { dao.updateAssignment(assignment.copy(isDeleted = false)) }
    }

    // Hard Delete (Gone Forever)
    fun deleteForever(assignment: Assignment) {
        viewModelScope.launch { dao.deleteAssignment(assignment) }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashedAssignments.forEach { dao.deleteAssignment(it) }
        }
    }

    // Helper for "Urgent" / "Completed" tabs
    fun getFilteredAssignments(): List<Assignment> {
        return when (filterType) {
            "Completed" -> assignments.filter { it.isCompleted }
            "Urgent" -> assignments.filter { it.isUrgent() }
            else -> assignments
        }
    }

    // --- NOTES (Standard) ---
    fun addNote(content: String) {
        viewModelScope.launch {
            dao.insertNote(BrainNote(content = content, colorIndex = (0..3).random()))
        }
    }

    fun deleteNote(note: BrainNote) {
        viewModelScope.launch { dao.deleteNote(note) }
    }

    // --- SUBJECTS (Standard) ---
    fun addSubject(name: String, code: String, units: Int, days: List<String>, sHour: Int, sMin: Int, eHour: Int, eMin: Int) {
        viewModelScope.launch {
            val newSubject = Subject(
                name = name, code = code, units = units,
                days = days, startHour = sHour, startMinute = sMin, endHour = eHour, endMinute = eMin,
                colorIndex = (0..5).random(),
                yearLevel = selectedYear, semester = selectedSem
            )
            dao.insertSubject(newSubject)
        }
    }

    fun updateSubjectGrade(subject: Subject, newGrade: Double) {
        viewModelScope.launch { dao.updateSubject(subject.copy(grade = newGrade)) }
    }

    fun deleteSubject(subject: Subject) {
        viewModelScope.launch { dao.deleteSubject(subject) }
    }

    // --- CALCULATIONS ---
    fun calculateSemGWA(): Double {
        val filtered = subjects.filter { it.yearLevel == selectedYear && it.semester == selectedSem && it.grade != null }
        if (filtered.isEmpty()) return 0.0
        val totalUnits = filtered.sumOf { it.units }
        val totalPoints = filtered.sumOf { (it.grade ?: 0.0) * it.units }
        return if (totalUnits > 0) totalPoints / totalUnits else 0.0
    }

    fun getProgress(): Float = if (assignments.isEmpty()) 0f else assignments.count { it.isCompleted }.toFloat() / assignments.size.toFloat()

    // --- PROFILE & TIMER (Keep Existing Logic) ---

    fun updateProfile(name: String, school: String, bio: String, avatarId: Int, imageUri: String?, category: String) {
        val finalImageUri = if (imageUri != null && imageUri.startsWith("content://")) {
            copyUriToInternalStorage(android.net.Uri.parse(imageUri))
        } else {
            imageUri
        }
        userProfile = UserProfile(name, school, bio, avatarId, finalImageUri, category)
        saveProfile()
    }

    private fun copyUriToInternalStorage(uri: android.net.Uri): String? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, fileName)
            context.filesDir.listFiles()?.forEach {
                if (it.name.startsWith("profile_") && it.name != fileName) {
                    it.delete()
                }
            }
            file.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            android.net.Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveProfile() {
        prefs.edit().putString("profile", gson.toJson(userProfile)).apply()
    }

    private fun loadProfile() {
        val profileJson = prefs.getString("profile", null)
        if (profileJson != null) userProfile = gson.fromJson(profileJson, UserProfile::class.java)
    }

    fun toggleTimer() { if (isTimerRunning) pauseTimer() else startTimer() }
    private fun startTimer() { isTimerRunning = true; timerJob = viewModelScope.launch { while (timeLeft > 0) { delay(1000L); timeLeft -= 1000L }; isTimerRunning = false } }
    private fun pauseTimer() { isTimerRunning = false; timerJob?.cancel() }
    fun resetTimer() { pauseTimer(); timeLeft = timerDuration }
    fun setDuration(minutes: Int) { resetTimer(); timerDuration = minutes * 60 * 1000L; timeLeft = timerDuration }

    private fun scheduleNotification(assignment: Assignment) {
        val intent = Intent(getApplication(), NotificationReceiver::class.java).apply {
            putExtra("TITLE", "Deadline: ${assignment.title}")
            putExtra("MESSAGE", "Your ${assignment.category} task is due today!")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(), assignment.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
                else alarmManager.set(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
            } else alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
        } catch (e: SecurityException) { e.printStackTrace() }
    }
}

// --- 5. UI (COMPOSABLES) ---

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = DeepViolet, secondary = ElectricTeal, background = OffWhite, surface = CardWhite)) {
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent(viewModel: StudentViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(2000); showSplash = false }
    if (showSplash) SplashScreen() else StudentApp(viewModel)
}

@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepViolet, SoftViolet))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.School, "Logo", tint = Color.White, modifier = Modifier.size(100.dp))
            Spacer(Modifier.height(16.dp))
            Text("Student Manager", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(color = ElectricTeal)
        }
    }
}

@Composable
fun StudentApp(viewModel: StudentViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(onClick = { showAddDialog = true }, containerColor = DeepViolet, contentColor = Color.White) { Icon(Icons.Default.Add, "Add") }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Assignment, "Tasks") }, label = { Text("Tasks") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Lightbulb, "Notes") }, label = { Text("Notes") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Timer, "Focus") }, label = { Text("Focus") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Book, "Subjects") }, label = { Text("Subjects") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(OffWhite).padding(padding)) {
            // ABOUT CLICK HANDLER
            if (selectedTab != 2) TopHeader(viewModel.userProfile, { showProfileDialog = true }, { showAboutDialog = true })
            when (selectedTab) {
                0 -> { DashboardSection(viewModel.getProgress()); AssignmentList(viewModel) }
                1 -> NoteGrid(viewModel.notes) { viewModel.deleteNote(it) }
                2 -> FocusScreen(viewModel)
                3 -> SubjectsScreen(viewModel)
            }
        }
        if (showAddDialog) {
            when (selectedTab) {
                // Updated Dialog Call
                0 -> AddAssignmentDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { t, d, c, color, bold, large ->
                        viewModel.addAssignment(t, d, c, color, bold, large)
                        showAddDialog = false
                    }
                )
                1 -> AddNoteDialog({ showAddDialog = false }) { c -> viewModel.addNote(c); showAddDialog = false }
                3 -> AddSubjectDialog({ showAddDialog = false }) { name, code, units, days, sh, sm, eh, em ->
                    viewModel.addSubject(name, code, units, days, sh, sm, eh, em)
                    showAddDialog = false
                }
            }
        }
        if (showProfileDialog) {
            ProfileDialog(viewModel.userProfile, { showProfileDialog = false }) { n, s, b, a, i, c -> viewModel.updateProfile(n, s, b, a, i, c); showProfileDialog = false }
        }
        if (showAboutDialog) {
            AboutDialog { showAboutDialog = false }
        }
    }
}

// --- UI SECTIONS ---
@Composable
fun TopHeader(profile: UserProfile, onProfileClick: () -> Unit, onAboutClick: () -> Unit) {
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star, Icons.Default.AccountCircle, Icons.Default.Pets)
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hello, ${profile.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
            Text("${profile.category} • ${profile.school}", style = MaterialTheme.typography.bodySmall, color = TextGray)
            Spacer(Modifier.height(4.dp))
            Text("\"${profile.bio}\"", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = DeepViolet)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = SoftViolet.copy(alpha = 0.2f), modifier = Modifier.size(60.dp).clickable { onProfileClick() }) {
                Box(contentAlignment = Alignment.Center) {
                    if (profile.imageUri != null) coil.compose.AsyncImage(model = profile.imageUri, contentDescription = "Profile", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Icon(avatars[profile.avatarId.coerceIn(0, avatars.lastIndex)], "Profile", tint = DeepViolet, modifier = Modifier.size(32.dp))
                }
            }
            IconButton(onClick = onAboutClick, modifier = Modifier.size(24.dp).padding(top = 4.dp)) {
                Icon(Icons.Default.Info, "About", tint = TextGray)
            }
        }
    }
}

@Composable
fun DashboardSection(progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(durationMillis = 1000), label = "progress")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = DeepViolet), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your Progress", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", color = SoftTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), color = ElectricTeal, trackColor = SoftViolet)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentList(viewModel: StudentViewModel) {
    var assignmentToEdit by remember { mutableStateOf<Assignment?>(null) }
    var assignmentToDelete by remember { mutableStateOf<Assignment?>(null) }

    if (viewModel.showTrashBin) {
        TrashBinScreen(viewModel)
        return
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = { viewModel.showTrashBin = true }) {
                Icon(Icons.Default.DeleteSweep, "Trash Bin", tint = TextGray)
            }
        }

        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = { Text("Search tasks...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepViolet,
                unfocusedBorderColor = Color.LightGray,
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite
            )
        )

        Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Urgent", "Completed").forEach { type ->
                FilterChip(
                    selected = viewModel.filterType == type,
                    onClick = { viewModel.filterType = type },
                    label = { Text(type) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DeepViolet,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        val filteredList = viewModel.getFilteredAssignments()
        LazyColumn(
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredList.isEmpty()) {
                item {
                    Text(
                        "No tasks found.",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
            items(filteredList, key = { it.id }) { item ->
                AssignmentCard(
                    item = item,
                    onToggle = { viewModel.toggleAssignment(item) },
                    onEdit = { assignmentToEdit = item },
                    onDeleteRequest = { assignmentToDelete = item }
                )
            }
        }
    }

    if (assignmentToEdit != null) {
        AddAssignmentDialog(
            onDismiss = { assignmentToEdit = null },
            onConfirm = { title, deadline, category, color, bold, large ->
                viewModel.updateAssignment(assignmentToEdit!!.copy(
                    title = title,
                    deadline = deadline,
                    category = category,
                    colorIndex = color,
                    isBold = bold,
                    isLarge = large
                ))
                assignmentToEdit = null
            },
            isEditMode = true,
            initialTitle = assignmentToEdit!!.title,
            initialCategory = assignmentToEdit!!.category,
            initialDate = assignmentToEdit!!.deadline,
            initialColor = assignmentToEdit!!.colorIndex,
            initialBold = assignmentToEdit!!.isBold,
            initialLarge = assignmentToEdit!!.isLarge
        )
    }

    if (assignmentToDelete != null) {
        AlertDialog(
            onDismissRequest = { assignmentToDelete = null },
            title = { Text("Move to Trash?") },
            text = { Text("Are you sure you want to remove '${assignmentToDelete?.title}'? You can restore it from the Trash Bin.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.moveAssignmentToTrash(assignmentToDelete!!)
                        assignmentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { assignmentToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AssignmentCard(
    item: Assignment,
    onToggle: (Assignment) -> Unit,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    // Determine Card Color: Use custom color if not completed, else gray out
    val cardColor = if (item.isCompleted) OffWhite else TaskColors[item.colorIndex % TaskColors.size]
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
    val badgeColor = when (item.category) { "Activity" -> CatActivity; "Quiz" -> CatQuiz; "Major Exam" -> CatExam; "Thesis/Research" -> CatThesis; else -> CatOrg }
    val borderStroke = if (item.isUrgent()) androidx.compose.foundation.BorderStroke(1.dp, WarningRed) else null

    // Typography Settings based on User Preference
    val fontWeight = if (item.isBold) FontWeight.Bold else FontWeight.SemiBold
    val fontSize = if (item.isLarge) 20.sp else 16.sp

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(if (item.isCompleted) 0.dp else 4.dp),
        border = borderStroke
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconToggleButton(
                checked = item.isCompleted,
                onCheckedChange = { onToggle(item) },
                colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = OffWhite, contentColor = Color.Gray, checkedContainerColor = ElectricTeal, checkedContentColor = Color.White)
            ) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done") else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // APPLIED CUSTOM FONT SIZE AND WEIGHT HERE
                Text(
                    text = item.title,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = textColor,
                    textDecoration = textDeco
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) { Text(item.category, color = badgeColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp, 2.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Text(if(item.isUrgent()) "DUE SOON: ${item.getFormattedDate()}" else item.getFormattedDate(), style = MaterialTheme.typography.labelMedium, color = if(item.isUrgent()) WarningRed else TextGray)
                }
            }
            Column {
                IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Edit, "Edit", tint = DeepViolet)
                }
                Spacer(Modifier.height(8.dp))
                IconButton(onClick = onDeleteRequest, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TrashBinScreen(viewModel: StudentViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
            IconButton(onClick = { viewModel.showTrashBin = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            Text("Trash Bin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (viewModel.trashedAssignments.isNotEmpty()) {
                TextButton(onClick = { viewModel.emptyTrash() }) {
                    Text("Empty Trash", color = WarningRed)
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(viewModel.trashedAssignments) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(0.2f))) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, textDecoration = TextDecoration.LineThrough)
                            Text("Deleted", style = MaterialTheme.typography.bodySmall, color = WarningRed)
                        }
                        IconButton(onClick = { viewModel.restoreAssignment(item) }) {
                            Icon(Icons.Default.Restore, "Restore", tint = ElectricTeal)
                        }
                        IconButton(onClick = { viewModel.deleteForever(item) }) {
                            Icon(Icons.Default.DeleteForever, "Delete Forever", tint = TextGray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    // State to track which note is being deleted
    var noteToDelete by remember { mutableStateOf<BrainNote?>(null) }

    // DELETE CONFIRMATION DIALOG
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(noteToDelete!!) // Call the actual delete
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text("Brain Dump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(notes) { note ->
            val noteColors = listOf(Color(0xFFFFF9C4), Color(0xFFE1BEE7), Color(0xFFB2DFDB), Color(0xFFFFCCBC))
            Card(
                colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]),
                modifier = Modifier.height(150.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(note.content, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                    IconButton(
                        onClick = { noteToDelete = note }, // Trigger the dialog
                        modifier = Modifier.align(Alignment.End).size(24.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun FocusScreen(viewModel: StudentViewModel) {
    val progress = if (viewModel.timerDuration > 0) viewModel.timeLeft.toFloat() / viewModel.timerDuration.toFloat() else 0f

    // NEW: Custom Timer State
    var showCustomTimerDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("") }

    if (showCustomTimerDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTimerDialog = false },
            title = { Text("Set Custom Timer") },
            text = {
                OutlinedTextField(
                    value = customMinutesInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) customMinutesInput = it },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = customMinutesInput.toIntOrNull()
                        if (mins != null && mins > 0) {
                            viewModel.setDuration(mins)
                            showCustomTimerDialog = false
                            customMinutesInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTimerDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Focus Mode", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DeepViolet)
        Text("Stay productive.", style = MaterialTheme.typography.bodyMedium, color = TextGray)
        Spacer(Modifier.height(48.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), color = ElectricTeal, trackColor = SoftViolet.copy(0.2f), strokeWidth = 12.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(viewModel.timeLeft), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, color = TextDark)
                Text(if (viewModel.isTimerRunning) "Focusing..." else "Paused", color = if(viewModel.isTimerRunning) ElectricTeal else Color.Gray, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ viewModel.resetTimer() }, modifier = Modifier.size(56.dp).background(OffWhite, CircleShape).border(1.dp, Color.LightGray, CircleShape)) { Icon(Icons.Outlined.Refresh, "Reset", tint = TextDark) }
            IconButton({ viewModel.toggleTimer() }, modifier = Modifier.size(80.dp).background(DeepViolet, CircleShape).shadow(8.dp, CircleShape)) {
                Icon(if (viewModel.isTimerRunning) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle, "Toggle", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(32.dp))
        // Updated Chips Row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 25, 45).forEach { mins ->
                FilterChip(
                    selected = viewModel.timerDuration == mins * 60 * 1000L,
                    onClick = { viewModel.setDuration(mins) },
                    label = { Text("$mins min") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftViolet, selectedLabelColor = Color.White)
                )
            }
            // Custom Option
            FilterChip(
                selected = false,
                onClick = { showCustomTimerDialog = true },
                label = { Text("Custom") },
                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftViolet, selectedLabelColor = Color.White)
            )
        }
    }
}

// --- NEW SUBJECTS & SCHEDULE SCREEN ---

@Composable
fun SubjectsScreen(viewModel: StudentViewModel) {
    val currentSubjects = viewModel.subjects.filter { it.yearLevel == viewModel.selectedYear && it.semester == viewModel.selectedSem }
    val gwa = viewModel.calculateSemGWA()
    var showGradeDialog by remember { mutableStateOf<Subject?>(null) }
    var isGridView by remember { mutableStateOf(false) }

    // NEW: State for Delete Confirmation
    var subjectToDelete by remember { mutableStateOf<Subject?>(null) }

    if (subjectToDelete != null) {
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text("Delete Subject?") },
            text = { Text("Are you sure you want to delete '${subjectToDelete?.code}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubject(subjectToDelete!!)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningRed)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Academic Year", style = MaterialTheme.typography.labelMedium, color = TextGray)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            YearSemDropdown(viewModel.selectedYear, listOf("1st Year", "2nd Year", "3rd Year", "4th Year", "5th Year")) { viewModel.selectedYear = it }
            YearSemDropdown(viewModel.selectedSem, listOf("1st Sem", "2nd Sem", "Summer")) { viewModel.selectedSem = it }
        }
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DeepViolet), elevation = CardDefaults.cardElevation(8.dp)) {
            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Semester GWA", color = Color.White.copy(0.8f))
                    Text(if (gwa > 0) "%.2f".format(gwa) else "No Grades", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Gold)
                }
                Icon(Icons.Outlined.Grade, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${viewModel.selectedYear} Subjects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { isGridView = !isGridView }) {
                Icon(if (isGridView) Icons.Default.List else Icons.Outlined.Schedule, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isGridView) "List View" else "Schedule View")
            }
        }

        if (currentSubjects.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Text("No subjects added.", color = TextGray)
                    Text("Tap + to enroll subjects.", color = TextGray)
                }
            }
        } else {
            if (isGridView) {
                ScheduleGrid(currentSubjects)
            } else {
                LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    items(currentSubjects, key = { it.id }) { subject ->
                        SubjectItem(
                            subject = subject,
                            onClick = { showGradeDialog = subject },
                            onDelete = { subjectToDelete = subject } // Trigger the confirmation dialog
                        )
                    }
                }
            }
        }
    }
    if (showGradeDialog != null) {
        AddGradeDialog(showGradeDialog!!, { showGradeDialog = null }) { viewModel.updateSubjectGrade(showGradeDialog!!, it); showGradeDialog = null }
    }
}

@Composable
fun ScheduleGrid(subjects: List<Subject>) {
    val days = listOf("M", "T", "W", "Th", "F", "S")
    val startHour = 7
    val endHour = 19
    val hourHeight = 60.dp

    val scrollState = rememberScrollState()

    Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
        Column(modifier = Modifier.width(40.dp).padding(top = 20.dp)) {
            for (h in startHour..endHour) {
                Text(
                    text = if (h > 12) "${h - 12} PM" else if(h==12) "12 PM" else "$h AM",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray,
                    modifier = Modifier.height(hourHeight)
                )
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            days.forEachIndexed { index, day ->
                val daySubjects = subjects.filter { it.days.contains(day) }

                Box(modifier = Modifier.weight(1f).height(hourHeight * (endHour - startHour + 1)).border(0.5.dp, Color.LightGray.copy(0.3f))) {
                    Text(day, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp), fontWeight = FontWeight.Bold, color = DeepViolet)

                    daySubjects.forEach { sub ->
                        val topOffset = ((sub.startHour - startHour) * 60 + sub.startMinute) * (1.0)
                        val duration = ((sub.endHour * 60 + sub.endMinute) - (sub.startHour * 60 + sub.startMinute))
                        val height = duration * 1.0

                        Card(
                            colors = CardDefaults.cardColors(containerColor = ScheduleColors[sub.colorIndex % ScheduleColors.size]),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(1.dp)
                                .offset(y = topOffset.dp)
                                .height(height.dp)
                        ) {
                            Column(modifier = Modifier.padding(2.dp)) {
                                Text(sub.code, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 8.sp, maxLines = 1)
                                Text(sub.name, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.9f), fontSize = 7.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SubjectItem(subject: Subject, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardWhite), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = OffWhite, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(50.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(subject.units.toString(), fontWeight = FontWeight.Bold, color = DeepViolet)
                    Text("Units", style = MaterialTheme.typography.labelSmall, color = TextGray, fontSize = 9.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(subject.code, style = MaterialTheme.typography.labelSmall, color = ElectricTeal, fontWeight = FontWeight.Bold)
                Text(subject.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subject.scheduleString, style = MaterialTheme.typography.bodySmall, color = TextGray)
            }
            if (subject.grade != null) {
                val g = subject.grade
                val color = if (g <= 1.25) GradeA else if (g <= 1.75) GradeB else if (g <= 2.5) GradeC else if (g <= 3.0) GradeD else GradeF
                Text("%.1f".format(g), fontWeight = FontWeight.Bold, color = color)
            } else Text("--", color = Color.LightGray)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Delete, "Del", tint = Color.LightGray) }
        }
    }
}

// --- DIALOGS (Enhanced) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubjectDialog(onDismiss: () -> Unit, onConfirm: (String, String, Int, List<String>, Int, Int, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var units by remember { mutableStateOf("3") }

    val days = listOf("M", "T", "W", "Th", "F", "S")
    val selectedDays = remember { mutableStateListOf<String>() }

    val timeStateStart = rememberTimePickerState(8, 0, false)
    val timeStateEnd = rememberTimePickerState(9, 30, false)
    var showStartTime by remember { mutableStateOf(false) }
    var showEndTime by remember { mutableStateOf(false) }

    if(showStartTime) {
        TimePickerDialog(onDismiss = { showStartTime = false }, onConfirm = { showStartTime = false }) { TimeInput(timeStateStart) }
    }
    if(showEndTime) {
        TimePickerDialog(onDismiss = { showEndTime = false }, onConfirm = { showEndTime = false }) { TimeInput(timeStateEnd) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Subject Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (e.g. IT 101)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = units, onValueChange = { if(it.all {c->c.isDigit()}) units = it }, label = { Text("Units") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

                Text("Class Days:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        FilterChip(selected = selectedDays.contains(day), onClick = { if(selectedDays.contains(day)) selectedDays.remove(day) else selectedDays.add(day) }, label = { Text(day) })
                    }
                }

                Text("Time:", style = MaterialTheme.typography.labelSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedTextField(
                        value = "${timeStateStart.hour%12}:${String.format("%02d", timeStateStart.minute)} ${if(timeStateStart.hour>=12) "PM" else "AM"}",
                        onValueChange = {}, readOnly = true, label = { Text("Start") }, modifier = Modifier.weight(1f).clickable { showStartTime = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextDark, disabledBorderColor = Color.Gray, disabledLabelColor = TextGray)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = "${timeStateEnd.hour%12}:${String.format("%02d", timeStateEnd.minute)} ${if(timeStateEnd.hour>=12) "PM" else "AM"}",
                        onValueChange = {}, readOnly = true, label = { Text("End") }, modifier = Modifier.weight(1f).clickable { showEndTime = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = TextDark, disabledBorderColor = Color.Gray, disabledLabelColor = TextGray)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotEmpty() && code.isNotEmpty() && selectedDays.isNotEmpty()) onConfirm(name, code, units.toIntOrNull()?:3, selectedDays, timeStateStart.hour, timeStateStart.minute, timeStateEnd.hour, timeStateEnd.minute) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Enroll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, text = { content() })
}

// --- OTHER DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssignmentDialog(
    onDismiss: () -> Unit,
    isEditMode: Boolean = false,
    initialTitle: String = "",
    initialCategory: String = "Activity",
    initialDate: Long = System.currentTimeMillis(),
    initialColor: Int = 0,
    initialBold: Boolean = false,
    initialLarge: Boolean = false,
    onConfirm: (String, Long, String, Int, Boolean, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    // NEW STATES
    var selectedColor by remember { mutableIntStateOf(initialColor) }
    var isBold by remember { mutableStateOf(initialBold) }
    var isLarge by remember { mutableStateOf(initialLarge) }

    val categories = listOf("Activity", "Quiz", "Major Exam", "Thesis/Research", "Org Work")
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = initialDate
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate)
    val timePickerState = rememberTimePickerState(initialHour = calendar.get(Calendar.HOUR_OF_DAY), initialMinute = calendar.get(Calendar.MINUTE), is24Hour = false)
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeText = String.format("%02d:%02d %s", if(timePickerState.hour % 12 == 0) 12 else timePickerState.hour % 12, timePickerState.minute, if(timePickerState.hour >= 12) "PM" else "AM")

    if (showDatePicker) DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ showDatePicker = false }) { Text("OK") } }) { DatePicker(state = datePickerState) }
    if (showTimePicker) TimePickerDialog(onDismiss = { showTimePicker = false }, onConfirm = { showTimePicker = false }) { TimeInput(state = timePickerState) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(if(isEditMode) "Edit Task" else "New Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dateFormat.format(Date(datePickerState.selectedDateMillis ?: initialDate)), onValueChange = {}, label = { Text("Date") }, readOnly = true, trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Default.DateRange, "Pick Date") } }, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true })
                OutlinedTextField(value = timeText, onValueChange = {}, label = { Text("Time") }, readOnly = true, trailingIcon = { IconButton({ showTimePicker = true }) { Icon(Icons.Default.AccessTime, "Pick Time") } }, modifier = Modifier.fillMaxWidth().clickable { showTimePicker = true })

                // CATEGORY CHIPS
                Text("Category:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    categories.take(3).forEach { cat ->
                        FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat, fontSize = 10.sp) })
                    }
                }

                // COLOR PICKER
                Text("Color:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColor == index) 2.dp else 1.dp,
                                    color = if (selectedColor == index) DeepViolet else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = index }
                        )
                    }
                }

                // TEXT STYLES (BOLD / LARGE)
                Text("Appearance:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBold, onCheckedChange = { isBold = it })
                    Text("Bold Title")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = isLarge, onCheckedChange = { isLarge = it })
                    Text("Large Text")
                }
            }
        },
        confirmButton = {
            Button({
                if (title.isNotEmpty()) {
                    val c = Calendar.getInstance();
                    c.timeInMillis = datePickerState.selectedDateMillis ?: initialDate;
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour);
                    c.set(Calendar.MINUTE, timePickerState.minute);
                    // Pass new values
                    onConfirm(title, c.timeInMillis, selectedCategory, selectedColor, isBold, isLarge)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text(if(isEditMode) "Save Changes" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Quick Thought") }, text = { OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3) }, confirmButton = { Button({ if (content.isNotEmpty()) onConfirm(content) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AddGradeDialog(subject: Subject, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var gradeInput by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Enter Final Grade") }, text = { Column { Text("For ${subject.code}", style = MaterialTheme.typography.bodySmall, color = TextGray); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = gradeInput, onValueChange = { gradeInput = it }, label = { Text("Grade (e.g. 1.50)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) } }, confirmButton = { Button(onClick = { val g = gradeInput.toDoubleOrNull(); if (g != null) onConfirm(g) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save Grade") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onConfirm: (String, String, String, Int, String?, String) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var school by remember { mutableStateOf(profile.school) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarId by remember { mutableIntStateOf(profile.avatarId) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(if (profile.imageUri != null) android.net.Uri.parse(profile.imageUri) else null) }
    val categories = listOf("Information Technology","Computer Science", "Education", "Business & Eco", "Arts & Design", "Engineering", "Medicine", "Law & Politics")
    var selectedCategory by remember { mutableStateOf(profile.category) }
    var expandedCategory by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: android.net.Uri? -> if (uri != null) imageUri = uri }
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star, Icons.Default.AccountCircle, Icons.Default.Pets)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Edit Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = DeepViolet) }, text = {
        LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            item { Box(contentAlignment = Alignment.BottomEnd) { Surface(shape = CircleShape, color = OffWhite, border = androidx.compose.foundation.BorderStroke(2.dp, ElectricTeal), modifier = Modifier.size(100.dp)) { if (imageUri != null) coil.compose.AsyncImage(model = imageUri, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Box(contentAlignment = Alignment.Center) { Icon(avatars[avatarId.coerceIn(0, avatars.lastIndex)], null, tint = DeepViolet, modifier = Modifier.size(50.dp)) } }; SmallFloatingActionButton(onClick = { galleryLauncher.launch("image/*") }, containerColor = Gold, contentColor = TextDark) { Icon(Icons.Default.Edit, "Pick Photo") } } }
            item { Text("Or pick an avatar:", style = MaterialTheme.typography.labelSmall, color = TextGray); Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) { avatars.forEachIndexed { index, icon -> IconButton(onClick = { avatarId = index; imageUri = null }) { Icon(icon, contentDescription = null, tint = if (avatarId == index && imageUri == null) DeepViolet else Color.LightGray, modifier = Modifier.size(if (avatarId == index && imageUri == null) 32.dp else 24.dp)) } } } }
            item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { ExposedDropdownMenuBox(expanded = expandedCategory, onExpandedChange = { expandedCategory = !expandedCategory }, modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value = selectedCategory, onValueChange = {}, readOnly = true, label = { Text("Major") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) }, modifier = Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) { categories.forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { selectedCategory = category; expandedCategory = false }) } } } }
            item { OutlinedTextField(value = school, onValueChange = { school = it }, label = { Text("School") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 3) }
        }
    }, confirmButton = { Button(onClick = { onConfirm(name, school, bio, avatarId, imageUri?.toString(), selectedCategory) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val facebookUrl = "https://www.facebook.com/markjomar.calmateo.1"
    val linkedinUrl = "https://www.linkedin.com/in/mark-jomar-calmateo-684834366/"
    val githubUrl = "https://github.com/Jom-kowd"
    AlertDialog(onDismissRequest = onDismiss, icon = { Icon(Icons.Default.Info, null, tint = DeepViolet) }, title = { Text("About Student Manager", fontWeight = FontWeight.Bold) }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Version 1.0.0", style = MaterialTheme.typography.labelSmall, color = TextGray); HorizontalDivider()
            Text("Developed by:", style = MaterialTheme.typography.bodySmall, color = TextGray)
            Text("MARK JOMAR S. CALMATEO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DeepViolet)
            Text("</DEVELOPER>", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DeepViolet)
            Text("This app helps Filipino college students manage subjects, GWA, and deadlines.", style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp)); Text("Connect with me:", style = MaterialTheme.typography.labelSmall, color = TextGray)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconButton(onClick = { uriHandler.openUri(facebookUrl) }) { Icon(Icons.Default.ThumbUp, "Facebook", tint = Color(0xFF1877F2)) }
                IconButton(onClick = { uriHandler.openUri(linkedinUrl) }) { Icon(Icons.Default.Work, "LinkedIn", tint = Color(0xFF0077B5)) }
                IconButton(onClick = { uriHandler.openUri(githubUrl) }) { Icon(Icons.Default.Code, "GitHub", tint = TextDark) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearSemDropdown(value: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { FilterChip(selected = true, onClick = { expanded = true }, label = { Text(value) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = CardWhite, selectedLabelColor = DeepViolet), border = FilterChipDefaults.filterChipBorder(borderColor = Color.LightGray, selectedBorderColor = DeepViolet, enabled = true, selected = true)); DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false }) } } }
}

fun formatTime(millis: Long): String { val totalSeconds = millis / 1000; return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60) }