package com.akshit.attendance.ui.student

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class DocCategory(val label: String) {
    ALL("All"),
    CALENDARS("Calendars"),
    HOLIDAYS("Holidays"),
    STUDENT_FORMS("Student Forms"),
    EXAMS("Exams"),
    HOSTEL("Hostel & Admin")
}

data class UniversityDocument(
    val id: String,
    val title: String,
    val authority: String,
    val refNumber: String,
    val date: String,
    val category: DocCategory,
    val fileSize: String,
    val description: String,
    val officialLinkText: String
)

val sampleUniversityDocuments = listOf(
    UniversityDocument(
        id = "doc_acad_cal_2026",
        title = "Academic Calendar 2026–2027",
        authority = "Office of the Dean Academics, GBU",
        refNumber = "GBU.-010/Acad./021/2026-123",
        date = "22 May 2026",
        category = DocCategory.CALENDARS,
        fileSize = "340 KB",
        description = "Complete schedule for Monsoon & Winter Semesters, Mid-Sem, Lab/Practical, Back Paper & End-Semester Theory Examinations.",
        officialLinkText = "GBU_Academic_Calendar_2026-27.pdf"
    ),
    UniversityDocument(
        id = "doc_holiday_list_2026",
        title = "Official University Holiday Order 2026",
        authority = "Office of the In-charge Registrar, GBU",
        refNumber = "GBU-029/Admn./Pradhikari/43/2026-2070",
        date = "07 Jan 2026",
        category = DocCategory.HOLIDAYS,
        fileSize = "290 KB",
        description = "Approved list of 24 Public Holidays, Executive Orders (Guru Nanak Jayanti Nov 24), and GB Nagar District local holidays.",
        officialLinkText = "GBU_Holiday_Order_2026.pdf"
    ),
    UniversityDocument(
        id = "doc_student_email_form",
        title = "Student Official Email ID (@gbu.ac.in) Form",
        authority = "Central Computer Centre (CCC) / ICT Cell",
        refNumber = "ICT/GBU/Mail-Allotment/2026",
        date = "01 Aug 2026",
        category = DocCategory.STUDENT_FORMS,
        fileSize = "180 KB",
        description = "Application proforma for allotment of institutional G-Suite email account (@gbu.ac.in), password reset & Wi-Fi MAC registration.",
        officialLinkText = "GBU_Student_Email_Application_Form.pdf"
    ),
    UniversityDocument(
        id = "doc_back_paper_form",
        title = "Back Paper & Repeat Course Examination Form",
        authority = "Examination Division, GBU",
        refNumber = "EXAM/GBU/BP-Form/2026",
        date = "15 Sep 2026",
        category = DocCategory.EXAMS,
        fileSize = "210 KB",
        description = "Official registration form for appearing in Monsoon / Winter End-Semester Back Paper examinations and repeat course evaluations.",
        officialLinkText = "GBU_Back_Paper_Application_Form.pdf"
    ),
    UniversityDocument(
        id = "doc_hostel_outpass",
        title = "Hostel Outpass & Night Leave Permission Slip",
        authority = "Office of the Chief Warden (Boys/Girls Hostels)",
        refNumber = "CW/Hostel/Outpass-04",
        date = "10 Aug 2026",
        category = DocCategory.HOSTEL,
        fileSize = "140 KB",
        description = "Mandatory night outpass, weekend home visits, and parent consent verification slip for all hostel residents.",
        officialLinkText = "GBU_Hostel_Outpass_Proforma.pdf"
    ),
    UniversityDocument(
        id = "doc_no_dues_clearance",
        title = "University & Hostel No-Dues Clearance Form",
        authority = "Student Affairs & Accounts Division",
        refNumber = "SA/GBU/NoDues-Clearance",
        date = "01 Jul 2026",
        category = DocCategory.HOSTEL,
        fileSize = "165 KB",
        description = "Consolidated clearance form covering Central Library, Hostels, Sports Council, Laboratories, and Finance Division.",
        officialLinkText = "GBU_Consolidated_NoDues_Form.pdf"
    ),
    UniversityDocument(
        id = "doc_bonafide_cert",
        title = "Bonafide Student Certificate Application",
        authority = "Academic Section, GBU",
        refNumber = "ACAD/GBU/Bonafide-Req",
        date = "05 Aug 2026",
        category = DocCategory.STUDENT_FORMS,
        fileSize = "155 KB",
        description = "Request proforma for issuance of official Bonafide Certificate for education loans, passport verification, and external scholarships.",
        officialLinkText = "GBU_Bonafide_Certificate_Form.pdf"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversityDocumentsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocCategory.ALL) }

    val filteredDocuments = remember(searchQuery, selectedCategory) {
        sampleUniversityDocuments.filter { doc ->
            val matchesCategory = selectedCategory == DocCategory.ALL || doc.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.description.contains(searchQuery, ignoreCase = true) ||
                doc.authority.contains(searchQuery, ignoreCase = true) ||
                doc.refNumber.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    Scaffold(
        containerColor = SurfaceBg,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Documents & Forms",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        )
                        Text(
                            text = "Gautam Buddha University Official Downloads",
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = TextGray,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Search calendar, holiday, email form...",
                                fontSize = 13.sp,
                                color = TextGray
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.sp,
                                color = TextDark
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = TextGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Category Filter Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(DocCategory.values()) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        color = if (isSelected) AccentPurple else Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AccentPurple else CardBorder
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = category }
                    ) {
                        Text(
                            text = category.label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextDark
                        )
                    }
                }
            }

            // Documents List
            if (filteredDocuments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.FolderOff,
                            contentDescription = null,
                            tint = TextGray.copy(alpha = 0.5f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No documents match your filter",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try searching with different keywords",
                            fontSize = 12.sp,
                            color = TextGray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDocuments, key = { it.id }) { doc ->
                        DocumentItemCard(
                            doc = doc,
                            onView = { openDocumentViewer(context, doc) },
                            onDownload = { downloadDocument(context, doc) },
                            onShare = { shareDocument(context, doc) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentItemCard(
    doc: UniversityDocument,
    onView: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: PDF Icon Badge + Title + Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // PDF Type Icon
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "PDF",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        doc.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        doc.authority,
                        fontSize = 11.sp,
                        color = TextGray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = AccentPurple.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        doc.category.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPurple
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Metadata Row: Ref Number & Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    doc.refNumber,
                    fontSize = 10.sp,
                    color = TextGray,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${doc.date} • ${doc.fileSize}",
                    fontSize = 10.sp,
                    color = TextGray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons Row: View, Download, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View / Open Button (Primary action)
                Surface(
                    color = AccentPurple.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onView() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = "View",
                            tint = AccentPurple,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "View",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple
                        )
                    }
                }

                // Download Button
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDownload() }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "Download",
                            tint = TextDark,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Save",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }
                }

                // Share Button (Icon button)
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onShare() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = TextDark,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun openDocumentViewer(context: Context, doc: UniversityDocument) {
    Toast.makeText(
        context,
        "Opening ${doc.title}...",
        Toast.LENGTH_SHORT
    ).show()
}

private fun downloadDocument(context: Context, doc: UniversityDocument) {
    Toast.makeText(
        context,
        "Saved ${doc.officialLinkText} to Downloads",
        Toast.LENGTH_LONG
    ).show()
}

private fun shareDocument(context: Context, doc: UniversityDocument) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, doc.title)
        putExtra(
            Intent.EXTRA_TEXT,
            "${doc.title}\n${doc.authority}\nRef: ${doc.refNumber}\nDate: ${doc.date}\n\nOfficial University Document available on the GBU Attendance App."
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share ${doc.title}"))
}
