package com.example.studentmanager

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
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
val DangerRed = Color(0xFFE53935)
val PriorityHigh = Color(0xFFF44336)
val PriorityMed = Color(0xFFFF9800)
val PriorityLow = Color(0xFF4CAF50)

// --- 2. MODEL (DATA CLASSES) ---
data class Assignment(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val deadline: Long,
    val category: String,
    val priority: Int = 1, // 0=Low, 1=Med, 2=High
    val isCompleted: Boolean = false
) {
    fun getFormattedDateTime(): String {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
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
    val avatarId: Int = 0, // 0-3 for presets
    val customImageBase64: String? = null // For custom gallery image
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

// --- 4. VIEWMODEL ---
class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("student_app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    var assignments = mutableStateListOf<Assignment>()
        private set
    var notes = mutableStateListOf<BrainNote>()
        private set
    var userProfile by mutableStateOf(UserProfile())
        private set
    var searchQuery by mutableStateOf("")

    init {
        loadData()
    }

    // --- Actions ---
    fun updateProfile(name: String, school: String, bio: String, avatarId: Int, customBase64: String?) {
        userProfile = UserProfile(name, school, bio, avatarId, customBase64)
        saveData()
    }

    fun addAssignment(title: String, deadline: Long, category: String, priority: Int) {
        val newItem = Assignment(title = title, deadline = deadline, category = category, priority = priority)
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

    fun getFilteredAssignments(): List<Assignment> {
        if (searchQuery.isEmpty()) return assignments
        return assignments.filter { it.title.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }
    }

    private fun scheduleNotification(assignment: Assignment) {
        val intent = Intent(getApplication(), NotificationReceiver::class.java).apply {
            putExtra("TITLE", "Deadline: ${assignment.title}")
            putExtra("MESSAGE", "Time is up! Complete your ${assignment.category} task.")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            getApplication(),
            assignment.id.toInt(),
            intent,
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
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun saveData() {
        val editor = prefs.edit()
        editor.putString("assignments", gson.toJson(assignments))
        editor.putString("notes", gson.toJson(notes))
        editor.putString("profile", gson.toJson(userProfile))
        editor.apply()
    }

    private fun loadData() {
        val assignmentJson = prefs.getString("assignments", null)
        val noteJson = prefs.getString("notes", null)
        val profileJson = prefs.getString("profile", null)

        if (assignmentJson != null) {
            val type = object : TypeToken<List<Assignment>>() {}.type
            assignments.addAll(gson.fromJson(assignmentJson, type))
        }
        if (noteJson != null) {
            val type = object : TypeToken<List<BrainNote>>() {}.type
            notes.addAll(gson.fromJson(noteJson, type))
        }
        if (profileJson != null) {
            userProfile = gson.fromJson(profileJson, UserProfile::class.java)
        }
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

    if (showSplash) {
        SplashScreen()
    } else {
        StudentApp(viewModel)
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepViolet, SoftViolet))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.School, "Logo", tint = Color.White, modifier = Modifier.size(100.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Student Manager", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
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
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }, containerColor = DeepViolet, contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        },
        bottomBar = {
            NavigationBar(containerColor = CardWhite) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
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
            TopHeader(profile = viewModel.userProfile, onProfileClick = { showProfileDialog = true })

            if (selectedTab == 0) {
                // Search Bar
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    placeholder = { Text("Search tasks...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardWhite,
                        unfocusedContainerColor = CardWhite,
                        focusedBorderColor = DeepViolet,
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                DashboardSection(progress = viewModel.getProgress())

                AssignmentList(
                    assignments = viewModel.getFilteredAssignments(),
                    onToggle = { viewModel.toggleAssignment(it) },
                    onDelete = { viewModel.deleteAssignment(it) }
                )
            } else {
                NoteGrid(notes = viewModel.notes, onDelete = { viewModel.deleteNote(it) })
            }
        }

        if (showAddDialog) {
            if (selectedTab == 0) {
                AddAssignmentDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { t, d, c, p ->
                        viewModel.addAssignment(t, d, c, p)
                        showAddDialog = false
                    }
                )
            } else {
                AddNoteDialog(
                    onDismiss = { showAddDialog = false },
                    onConfirm = { content -> viewModel.addNote(content); showAddDialog = false }
                )
            }
        }

        if (showProfileDialog) {
            ProfileDialog(
                profile = viewModel.userProfile,
                onDismiss = { showProfileDialog = false },
                onConfirm = { n, s, b, a, img ->
                    viewModel.updateProfile(n, s, b, a, img)
                    showProfileDialog = false
                }
            )
        }
    }
}

// --- UI COMPONENTS ---

@Composable
fun TopHeader(profile: UserProfile, onProfileClick: () -> Unit) {
    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hello, ${profile.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = TextDark)
            Text(profile.school.ifEmpty { "Ready to learn?" }, style = MaterialTheme.typography.bodySmall, color = TextGray)
        }
        Spacer(modifier = Modifier.width(16.dp))

        Surface(
            shape = CircleShape,
            color = SoftViolet.copy(alpha = 0.2f),
            modifier = Modifier.size(50.dp).clickable { onProfileClick() }
        ) {
            if (profile.customImageBase64 != null) {
                val bitmap = remember(profile.customImageBase64) {
                    val bytes = Base64.decode(profile.customImageBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                }
                Image(bitmap = bitmap, contentDescription = "Profile", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(avatars[profile.avatarId], contentDescription = "Profile", tint = DeepViolet, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
fun DashboardSection(progress: Float) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DeepViolet),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your Progress", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${(animatedProgress * 100).toInt()}%", color = SoftTeal, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = ElectricTeal,
                trackColor = SoftViolet
            )
        }
    }
}

@Composable
fun AssignmentList(assignments: List<Assignment>, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    if (assignments.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.DoneAll, null, modifier = Modifier.size(60.dp), tint = Color.LightGray)
            Text("No tasks found", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(assignments, key = { it.id }) { item -> AssignmentCard(item, onToggle, onDelete) }
        }
    }
}

@Composable
fun AssignmentCard(item: Assignment, onToggle: (Assignment) -> Unit, onDelete: (Assignment) -> Unit) {
    val cardColor = if (item.isCompleted) OffWhite else CardWhite
    val textColor = if (item.isCompleted) Color.Gray else TextDark
    val textDeco = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    val priorityColor = when(item.priority) {
        2 -> PriorityHigh
        1 -> PriorityMed
        else -> PriorityLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isCompleted) 0.dp else 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FilledIconToggleButton(
                checked = item.isCompleted,
                onCheckedChange = { onToggle(item) },
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = OffWhite, contentColor = Color.Gray, checkedContainerColor = ElectricTeal, checkedContentColor = Color.White
                )
            ) {
                if (item.isCompleted) Icon(Icons.Default.Check, "Done") else Icon(Icons.Default.Circle, "Pending", tint = Color.LightGray)
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Priority Line
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(priorityColor, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = textColor, textDecoration = textDeco)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = DeepViolet.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                        Text(item.category, color = DeepViolet, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(item.getFormattedDateTime(), style = MaterialTheme.typography.labelMedium, color = if(item.deadline < System.currentTimeMillis() && !item.isCompleted) DangerRed else TextGray)
                }
            }
            IconButton(onClick = { onDelete(item) }) { Icon(Icons.Outlined.Delete, "Delete", tint = Color.Gray) }
        }
    }
}

@Composable
fun NoteGrid(notes: List<BrainNote>, onDelete: (BrainNote) -> Unit) {
    if (notes.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(60.dp), tint = Color.LightGray)
            Text("No notes yet", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) { Text("Brain Dump", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp)) }
            items(notes) { note ->
                val noteColors = listOf(Color(0xFFFFF9C4), Color(0xFFE1BEE7), Color(0xFFB2DFDB), Color(0xFFFFCCBC))
                Card(colors = CardDefaults.cardColors(containerColor = noteColors[note.colorIndex % noteColors.size]), modifier = Modifier.height(150.dp)) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text(note.content, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { onDelete(note) }, modifier = Modifier.align(Alignment.End).size(24.dp)) { Icon(Icons.Outlined.Delete, "Delete", tint = TextDark.copy(alpha = 0.5f)) }
                    }
                }
            }
        }
    }
}

// --- DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssignmentDialog(onDismiss: () -> Unit, onConfirm: (String, Long, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Homework") }
    var priority by remember { mutableIntStateOf(1) } // 1=Med

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    var showDatePicker by remember { mutableStateOf(false) }

    // Time Logic
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    var selectedHour by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    val categories = listOf("Homework", "Exam", "Project", "Personal", "Club", "Work", "Quiz", "Meeting")

    // Time Picker Dialog Launch
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour: Int, minute: Int ->
            selectedHour = hour
            selectedMinute = minute
        }, selectedHour, selectedMinute, false
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    timePickerDialog.show() // Show time picker after date
                }) { Text("Next") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )

                // DateTime Display
                val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                val displayCal = Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }
                val dateText = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(displayCal.time)

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { },
                    label = { Text("Deadline") },
                    readOnly = true,
                    trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, "Pick") } },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
                )

                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = priority == 0, onClick = { priority = 0 }, label = { Text("Low") }, leadingIcon = { Icon(Icons.Default.Coffee, null, tint = PriorityLow) })
                    FilterChip(selected = priority == 1, onClick = { priority = 1 }, label = { Text("Med") }, leadingIcon = { Icon(Icons.Default.Bolt, null, tint = PriorityMed) })
                    FilterChip(selected = priority == 2, onClick = { priority = 2 }, label = { Text("High") }, leadingIcon = { Icon(Icons.Default.LocalFireDepartment, null, tint = PriorityHigh) })
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(selected = selectedCategory == cat, onClick = { selectedCategory = cat }, label = { Text(cat) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        val finalCal = Calendar.getInstance().apply {
                            timeInMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                        }
                        onConfirm(title, finalCal.timeInMillis, selectedCategory, priority)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
                minLines = 3, maxLines = 5
            )
        },
        confirmButton = { Button(onClick = { if (content.isNotEmpty()) onConfirm(content) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProfileDialog(profile: UserProfile, onDismiss: () -> Unit, onConfirm: (String, String, String, Int, String?) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var school by remember { mutableStateOf(profile.school) }
    var bio by remember { mutableStateOf(profile.bio) }
    var avatarId by remember { mutableIntStateOf(profile.avatarId) }
    var customImageBase64 by remember { mutableStateOf(profile.customImageBase64) }

    val context = LocalContext.current
    val contentResolver = context.contentResolver

    // Image Picker Launcher
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                // Compress and encode to Base64 for SharedPreferences
                val inputStream = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                // Resize to max 200px to save space
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
                val stream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                customImageBase64 = Base64.encodeToString(stream.toByteArray(), Base64.DEFAULT)
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val avatars = listOf(Icons.Default.Face, Icons.Default.SentimentVerySatisfied, Icons.Default.SmartToy, Icons.Default.Star)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Student ID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                // Avatar Selection
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Avatar", style = MaterialTheme.typography.labelMedium)
                    if (customImageBase64 != null) {
                        TextButton(onClick = { customImageBase64 = null }) { Text("Reset") }
                    }
                }

                if (customImageBase64 == null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        avatars.forEachIndexed { index, icon ->
                            Surface(shape = CircleShape, color = if (avatarId == index) DeepViolet else OffWhite, modifier = Modifier.size(40.dp).clickable { avatarId = index }) {
                                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (avatarId == index) Color.White else Color.Gray, modifier = Modifier.size(24.dp)) }
                            }
                        }
                        // Add Custom Image Button
                        Surface(shape = CircleShape, color = OffWhite, modifier = Modifier.size(40.dp).clickable { launcher.launch("image/*") }) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Image, null, tint = Color.Gray, modifier = Modifier.size(24.dp)) }
                        }
                    }
                } else {
                    // Show Selected Custom Image
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val bitmap = remember(customImageBase64) {
                            val bytes = Base64.decode(customImageBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                        }
                        Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp).clip(CircleShape).border(2.dp, DeepViolet, CircleShape).clickable { launcher.launch("image/*") })
                    }
                    Text("Tap image to change", style = MaterialTheme.typography.bodySmall, color = TextGray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = school, onValueChange = { school = it }, label = { Text("School / University") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio / Tagline") }, modifier = Modifier.fillMaxWidth())

                Divider()
                // Credits
                Card(colors = CardDefaults.cardColors(containerColor = TextDark), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = Gold, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Text("M", fontWeight = FontWeight.Bold, color = TextDark) } }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Developed by", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("Mark Jomar S. Calmateo", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Building the future 🚀", style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = Gold)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotEmpty()) onConfirm(name, school, bio, avatarId, customImageBase64) }, colors = ButtonDefaults.buttonColors(containerColor = DeepViolet)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}