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
import androidx.compose.animation.*
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration // <--- ADDED THIS IMPORT
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
import java.text.SimpleDateFormat
import java.util.*

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
val GradeA = Color(0xFF4CAF50)
val GradeB = Color(0xFF8BC34A)
val GradeC = Color(0xFFFFC107)
val GradeD = Color(0xFFFF9800)
val GradeF = Color(0xFFF44336)

// Category Colors
val CatHomework = Color(0xFFE57373)
val CatExam = Color(0xFFBA68C8)
val CatProject = Color(0xFF64B5F6)
val CatPersonal = Color(0xFFFFB74D)

// --- 2. MODEL (DATA CLASSES) ---
data class Assignment(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val deadline: Long,
    val category: String,
    val isCompleted: Boolean = false
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(deadline))
    }
}

data class BrainNote(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val colorIndex: Int = 0
)

data class UserProfile(
    val name: String = "Student",
    val school: String = "Android Academy",
    val bio: String = "Coding my future.",
    val avatarId: Int = 0
)

// NEW: Course Model for Grade Tracking
data class Course(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val credits: Int,
    val gradeLabel: String, // e.g., "A", "B+"
    val gradePoint: Double // e.g., 4.0, 3.3
)

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
    private val prefs = application.getSharedPreferences("student_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var assignments = mutableStateListOf<Assignment>()
        private set
    var notes = mutableStateListOf<BrainNote>()
        private set
    var courses = mutableStateListOf<Course>() // NEW: List of courses
        private set
    var userProfile by mutableStateOf(UserProfile())
        private set

    // Timer State
    var timerDuration by mutableLongStateOf(25 * 60 * 1000L)
        private set
    var timeLeft by mutableLongStateOf(25 * 60 * 1000L)
        private set
    var isTimerRunning by mutableStateOf(false)
        private set
    private var timerJob: Job? = null

    init {
        loadData()
    }

    // --- Profile & Data Actions ---
    fun updateProfile(name: String, school: String, bio: String, avatarId: Int) {
        userProfile = UserProfile(name, school, bio, avatarId)
        saveData()
    }

    fun addAssignment(title: String, deadline: Long, category: String) {
        val newItem = Assignment(title = title, deadline = deadline, category = category)
        assignments.add(0, newItem)
        saveData()
        scheduleNotification(newItem)
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

    // NEW: Course Actions
    fun addCourse(name: String, credits: Int, gradeLabel: String, gradePoint: Double) {
        courses.add(0, Course(name = name, credits = credits, gradeLabel = gradeLabel, gradePoint = gradePoint))
        saveData()
    }

    fun deleteCourse(course: Course) {
        courses.remove(course)
        saveData()
    }

    // NEW: Calculate GPA
    fun calculateGPA(): Double {
        if (courses.isEmpty()) return 0.0
        val totalPoints = courses.sumOf { it.gradePoint * it.credits }
        val totalCredits = courses.sumOf { it.credits }
        return if (totalCredits > 0) totalPoints / totalCredits else 0.0
    }

    // --- Timer Actions ---
    fun toggleTimer() {
        if (isTimerRunning) pauseTimer() else startTimer()
    }

    private fun startTimer() {
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft -= 1000L
            }
            isTimerRunning = false
        }
    }

    private fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
    }

    fun resetTimer() {
        pauseTimer()
        timeLeft = timerDuration
    }

    fun setDuration(minutes: Int) {
        resetTimer()
        timerDuration = minutes * 60 * 1000L
        timeLeft = timerDuration
    }

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
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, assignment.deadline, pendingIntent)
            }
        } catch (e: SecurityException) { e.printStackTrace() }
    }

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("assignments", gson.toJson(assignments))
        editor.putString("notes", gson.toJson(notes))
        editor.putString("courses", gson.toJson(courses))
        editor.putString("profile", gson.toJson(userProfile))
        editor.apply()
    }

    private fun loadData() {
        val assignmentJson = prefs.getString("assignments", null)
        val noteJson = prefs.getString("notes", null)
        val coursesJson = prefs.getString("courses", null)
        val profileJson = prefs.getString("profile", null)

        if (assignmentJson != null) assignments.addAll(gson.fromJson(assignmentJson, object : TypeToken<List<Assignment>>() {}.type))
        if (noteJson != null) notes.addAll(gson.fromJson(noteJson, object : TypeToken<List<BrainNote>>() {}.type))
        if (coursesJson != null) courses.addAll(gson.fromJson(coursesJson, object : TypeToken<List<Course>>() {}.type))
        if (profileJson != null) userProfile = gson.fromJson(profileJson, UserProfile::class.java)
    }

    fun getProgress(): Float {
        if (assignments.isEmpty()) return 0f
        return assignments.count { it.isCompleted }.toFloat() / assignments.size.toFloat()
    }
}

// --- 5. UI (COMPOSABLES) ---

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
                MainContent()
            }
        }
    }
}

@Composable
fun MainContent(viewModel: StudentViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplash = false
    }
    if (showSplash) SplashScreen() else StudentApp(viewModel)
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DeepViolet, SoftViolet))),
        contentAlignment = Alignment.Center
    ) {
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

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            // Show FAB for Assignments, Notes, and Grades
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = DeepViolet,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, "Add")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.AutoMirrored.Filled.Assignment, "Tasks") }, label = { Text("Tasks") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Lightbulb, "Notes") }, label = { Text("Notes") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.Timer, "Focus") }, label = { Text("Focus") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Outlined.Grade, "Grades") }, label = { Text("Grades") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(OffWhite).padding(padding)) {
            if (selectedTab != 2) TopHeader(viewModel.userProfile) { showProfileDialog = true }

            when (selectedTab) {
                0 -> {
                    DashboardSection(viewModel.getProgress())
                    AssignmentList(viewModel.assignments, { viewModel.toggleAssignment(it) }, { viewModel.deleteAssignment(it) })
                }
                1 -> NoteGrid(viewModel.notes) { viewModel.deleteNote(it) }
                2 -> FocusScreen(viewModel)
                3 -> GradesScreen(viewModel)
            }
        }

        if (showAddDialog) {
            when (selectedTab) {
                0 -> AddAssignmentDialog({ showAddDialog = false }) { t, d, c -> viewModel.addAssignment(t, d, c); showAddDialog = false }
                1 -> AddNoteDialog({ showAddDialog = false }) { c -> viewModel.addNote(c); showAddDialog = false }
                3 -> AddCourseDialog({ showAddDialog = false }) { n, cr, gl, gp -> viewModel.addCourse(n, cr, gl, gp); showAddDialog = false }
            }
        }

        if (showProfileDialog) {
            ProfileDialog(viewModel.userProfile, { showProfileDialog = false }) { n, s, b, a -> viewModel.updateProfile(n, s, b, a); showProfileDialog = false }
        }
    }
}

// --- UI SECTIONS ---

@Composable
fun TopHeader(profile: UserProfile, onProfileClick: () -> Unit) {
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hello, ${profile.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
            Text(profile.school.ifEmpty { "Ready to learn?" }, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
        Surface(shape = CircleShape, color = SoftViolet.copy(alpha = 0.2f), modifier = Modifier.size(50.dp).clickable { onProfileClick() }) {
            Box(contentAlignment = Alignment.Center) {
                Icon(avatars[profile.avatarId], "Profile", tint = DeepViolet, modifier = Modifier.size(32.dp))
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

@Composable
fun AssignmentList(assignments: List<Assignment>, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
        items(assignments, key = { it.id }) { item -> AssignmentCard(item, onToggle, onDelete) }
    }
}

@Composable
fun AssignmentCard(item: Assignment, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    val cardColor = if (item.isCompleted) OffWhite else CardWhite
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
    val badgeColor = when (item.category) { "Homework" -> CatHomework; "Exam" -> CatExam; "Project" -> CatProject; else -> CatPersonal }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cardColor), elevation = CardDefaults.cardElevation(if (item.isCompleted) 0.dp else 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconToggleButton(checked = item.isCompleted, onCheckedChange = { onToggle(item) }, colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = OffWhite, contentColor = Color.Gray, checkedContainerColor = ElectricTeal, checkedContentColor = Color.White)) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done") else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = textColor, textDecoration = textDeco)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.category, color = badgeColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(6.dp, 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(item.getFormattedDate(), style = MaterialTheme.typography.labelMedium, color = TextGray)
                }
            }
            IconButton({ onDelete(item) }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray) }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Text("Brain Dump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(notes) { note ->
            val noteColors = listOf(Color(0xFFFFF9C4), Color(0xFFE1BEE7), Color(0xFFB2DFDB), Color(0xFFFFCCBC))
            Card(colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]), modifier = Modifier.height(150.dp)) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Text(note.content, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                    IconButton({ onDelete(note) }, modifier = Modifier.align(Alignment.End).size(24.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(0.5f)) }
                }
            }
        }
    }
}

@Composable
fun FocusScreen(viewModel: StudentViewModel) {
    val progress = if (viewModel.timerDuration > 0) viewModel.timeLeft.toFloat() / viewModel.timerDuration.toFloat() else 0f
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(15, 25, 45).forEach { mins ->
                FilterChip(selected = viewModel.timerDuration == mins * 60 * 1000L, onClick = { viewModel.setDuration(mins) }, label = { Text("$mins min") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftViolet, selectedLabelColor = Color.White))
            }
        }
    }
}

// In MainActivity.kt

@Composable
fun GradesScreen(viewModel: StudentViewModel) {
    // FIX 1: Use Kotlin's safe formatting extension instead of String.format
    // This prevents potential locale or format crashes
    val gpa = viewModel.calculateGPA()
    val formattedGPA = "%.2f".format(gpa)

    LazyColumn(
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DeepViolet),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Cumulative GPA",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        formattedGPA,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Keep pushing! 🚀",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold
                    )
                }
            }
        }
        item {
            Text(
                "Courses",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (viewModel.courses.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No courses yet. Tap + to add one!", color = TextGray)
                }
            }
        } else {
            // FIX 2: Added a unique key for performance and stability
            items(viewModel.courses, key = { it.id }) { course ->
                CourseCard(course) { viewModel.deleteCourse(course) }
            }
        }
    }
}

@Composable
fun CourseCard(course: Course, onDelete: () -> Unit) {
    // FIX 3: Robust null safety. If data is corrupted (e.g., from Gson),
    // these fields might be null. We use "?:" to provide a default value so it doesn't crash.
    val gradeColor = when {
        course.gradePoint >= 4.0 -> GradeA
        course.gradePoint >= 3.0 -> GradeB
        course.gradePoint >= 2.0 -> GradeC
        course.gradePoint >= 1.0 -> GradeD
        else -> GradeF
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Safe access to name
                Text(
                    course.name ?: "Unknown Course",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    "${course.credits} Credits",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Surface(
                color = gradeColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                // Safe access to gradeLabel
                Text(
                    course.gradeLabel ?: "-",
                    color = gradeColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, "Delete", tint = Color.LightGray)
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

// --- DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssignmentDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Homework") }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ showDatePicker = false }) { Text("OK") } }) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(datePickerState.selectedDateMillis ?: System.currentTimeMillis())), onValueChange = {}, label = { Text("Deadline") }, readOnly = true, trailingIcon = { IconButton({ showDatePicker = true }) { Icon(Icons.Default.DateRange, "Pick Date") } }, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true })
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Homework", "Exam", "Project", "Personal").take(2).forEach { cat -> FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) }) }
                }
            }
        },
        confirmButton = { Button({ if (title.isNotEmpty()) onConfirm(title, datePickerState.selectedDateMillis ?: System.currentTimeMillis(), selectedCategory) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddNoteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Quick Thought") },
        text = { OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Content") }, minLines = 3) },
        confirmButton = { Button({ if (content.isNotEmpty()) onConfirm(content) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// NEW: Add Course Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseDialog(onDismiss: () -> Unit, onConfirm: (String, Int, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var credits by remember { mutableStateOf("3") }

    // Grade Options: Label mapped to Point
    val grades = listOf(
        "A" to 4.0, "A-" to 3.7, "B+" to 3.3, "B" to 3.0,
        "B-" to 2.7, "C+" to 2.3, "C" to 2.0, "D" to 1.0, "F" to 0.0
    )
    var expanded by remember { mutableStateOf(false) }
    var selectedGrade by remember { mutableStateOf(grades[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Course Grade") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Course Name (e.g., Math 101)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = credits,
                    onValueChange = { if (it.all { char -> char.isDigit() }) credits = it },
                    label = { Text("Credits (e.g., 3)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Dropdown for Grade
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = "${selectedGrade.first} (${selectedGrade.second})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Grade Achieved") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        grades.forEach { grade ->
                            DropdownMenuItem(
                                text = { Text("${grade.first} (${grade.second})") },
                                onClick = {
                                    selectedGrade = grade
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && credits.isNotEmpty()) {
                        onConfirm(name, credits.toIntOrNull() ?: 0, selectedGrade.first, selectedGrade.second)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Add Course") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onConfirm: (String, String, String, Int) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var school by remember { mutableStateOf(profile.school) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarId by remember { mutableIntStateOf(profile.avatarId) }
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    avatars.forEachIndexed { index, icon ->
                        Surface(shape = CircleShape, color = if (avatarId == index) DeepViolet else OffWhite, modifier = Modifier.size(50.dp).clickable { avatarId = index }.border(2.dp, if (avatarId == index) Gold else Color.Transparent, CircleShape)) {
                            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (avatarId == index) Color.White else Color.Gray, modifier = Modifier.size(30.dp)) }
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = school, onValueChange = { school = it }, label = { Text("School") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button({ if (name.isNotEmpty()) onConfirm(name, school, bio, avatarId) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}