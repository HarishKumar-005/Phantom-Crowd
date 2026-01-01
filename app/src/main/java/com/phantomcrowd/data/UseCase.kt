package com.phantomcrowd.data

import androidx.compose.ui.graphics.Color

/**
 * Use case categories for issue reporting.
 * Each use case represents a specific domain of community issues.
 */
enum class UseCase(
    val label: String,
    val icon: String,
    val color: Color,
    val description: String
) {
    WOMENS_SAFETY(
        label = "Women's Safety",
        icon = "👩",
        color = Color(0xFFFF0000),
        description = "Report safety concerns affecting women"
    ),
    ACCESSIBILITY(
        label = "Accessibility",
        icon = "♿",
        color = Color(0xFF0066FF),
        description = "Report accessibility barriers"
    ),
    LABOR_RIGHTS(
        label = "Labor Rights",
        icon = "👷",
        color = Color(0xFFFF9900),
        description = "Report workplace violations"
    ),
    FACILITIES(
        label = "Facilities",
        icon = "🏢",
        color = Color(0xFFFFCC00),
        description = "Report facility issues"
    ),
    ENVIRONMENTAL(
        label = "Environmental",
        icon = "🌍",
        color = Color(0xFF00CC66),
        description = "Report environmental hazards"
    ),
    CIVIL_RESISTANCE(
        label = "Civil Resistance",
        icon = "🎤",
        color = Color(0xFF9933FF),
        description = "Report civil rights issues"
    );

    companion object {
        fun fromString(value: String?): UseCase? {
            return entries.find { it.name == value }
        }
    }
}

/**
 * Severity levels for issues.
 * Determines priority and visual representation.
 */
enum class Severity(
    val label: String,
    val color: Color,
    val priority: Int,
    val icon: String
) {
    URGENT(
        label = "Urgent",
        color = Color(0xFFFF0000),
        priority = 1,
        icon = "🔴"
    ),
    HIGH(
        label = "High",
        color = Color(0xFFFF9900),
        priority = 2,
        icon = "🟠"
    ),
    MEDIUM(
        label = "Medium",
        color = Color(0xFFFFCC00),
        priority = 3,
        icon = "🟡"
    ),
    LOW(
        label = "Low",
        color = Color(0xFF00CC66),
        priority = 4,
        icon = "🟢"
    );

    companion object {
        fun fromString(value: String?): Severity {
            return entries.find { it.name == value } ?: MEDIUM
        }
    }
}

/**
 * Category within a use case.
 * Each use case has specific categories relevant to that domain.
 */
data class Category(
    val id: String,
    val label: String,
    val icon: String = "",
    val description: String = "",
    val defaultSeverity: Severity = Severity.MEDIUM
)

/**
 * Categories organized by use case.
 * Provides subcategories for each main use case.
 */
object UseCaseCategories {
    
    private val womensSafetyCategories = listOf(
        Category("ASSAULT", "Assault", "⚠️", "Physical assault or attack", Severity.URGENT),
        Category("HARASSMENT", "Harassment", "🚫", "Verbal or physical harassment", Severity.HIGH),
        Category("UNSAFE_AREA", "Unsafe Area", "🌙", "Poorly lit or dangerous location", Severity.HIGH),
        Category("NO_EMERGENCY_HELP", "No Emergency Help", "🆘", "Lack of emergency services", Severity.URGENT),
        Category("STALKING", "Stalking", "👁️", "Being followed or watched", Severity.URGENT),
        Category("OTHER", "Other", "📝", "Other safety concern", Severity.MEDIUM)
    )
    
    private val accessibilityCategories = listOf(
        Category("BROKEN_RAMP", "Broken Ramp", "🚧", "Damaged wheelchair ramp", Severity.HIGH),
        Category("NO_TOILET", "No Accessible Toilet", "🚻", "Missing accessible restroom", Severity.HIGH),
        Category("NO_ELEVATOR", "No Elevator", "🛗", "Missing or broken elevator", Severity.HIGH),
        Category("INACCESSIBLE_DOOR", "Inaccessible Door", "🚪", "Door too narrow or heavy", Severity.MEDIUM),
        Category("NO_CAPTIONS", "No Captions", "📺", "Missing captions or audio description", Severity.MEDIUM),
        Category("BLOCKED_PATH", "Blocked Path", "🚷", "Pathway blocked for wheelchairs", Severity.HIGH),
        Category("OTHER", "Other", "📝", "Other accessibility issue", Severity.MEDIUM)
    )
    
    private val laborRightsCategories = listOf(
        Category("WAGE_THEFT", "Wage Theft", "💰", "Unpaid wages or illegal deductions", Severity.URGENT),
        Category("SAFETY_VIOLATION", "Safety Violation", "⚠️", "Workplace safety hazard", Severity.URGENT),
        Category("HARASSMENT", "Harassment", "🚫", "Workplace harassment", Severity.HIGH),
        Category("EXCESSIVE_HOURS", "Excessive Hours", "⏰", "Forced overtime or long hours", Severity.HIGH),
        Category("NO_BENEFITS", "No Benefits", "🏥", "Denied benefits or insurance", Severity.MEDIUM),
        Category("CHILD_LABOR", "Child Labor", "🧒", "Illegal employment of minors", Severity.URGENT),
        Category("OTHER", "Other", "📝", "Other labor issue", Severity.MEDIUM)
    )
    
    private val facilitiesCategories = listOf(
        Category("BROKEN_EQUIPMENT", "Broken Equipment", "🔧", "Non-functional equipment", Severity.MEDIUM),
        Category("WATER_LEAK", "Water Leak", "💧", "Leaking pipes or flooding", Severity.HIGH),
        Category("ELECTRICAL", "Electrical Issue", "⚡", "Electrical hazard or outage", Severity.URGENT),
        Category("DIRTY", "Dirty/Unsanitary", "🧹", "Unclean conditions", Severity.MEDIUM),
        Category("PEST", "Pest Infestation", "🐀", "Insects or rodents", Severity.HIGH),
        Category("STRUCTURAL", "Structural Damage", "🏚️", "Building damage or unsafe structure", Severity.URGENT),
        Category("OTHER", "Other", "📝", "Other facility issue", Severity.MEDIUM)
    )
    
    private val environmentalCategories = listOf(
        Category("OVERFLOWING_TRASH", "Overflowing Trash", "🗑️", "Garbage overflow or illegal dumping", Severity.HIGH),
        Category("SPILL", "Hazardous Spill", "☣️", "Chemical or oil spill", Severity.URGENT),
        Category("POLLUTION", "Pollution", "🏭", "Air or water pollution", Severity.HIGH),
        Category("BAD_ODOR", "Bad Odor", "👃", "Foul smell from unknown source", Severity.MEDIUM),
        Category("DIRTY", "Dirty Area", "🌿", "Littered or neglected public space", Severity.LOW),
        Category("NOISE", "Noise Pollution", "📢", "Excessive noise levels", Severity.MEDIUM),
        Category("OTHER", "Other", "📝", "Other environmental issue", Severity.MEDIUM)
    )
    
    private val civilResistanceCategories = listOf(
        Category("POLICE_VIOLENCE", "Police Violence", "👮", "Excessive force by authorities", Severity.URGENT),
        Category("DETENTION", "Wrongful Detention", "⛓️", "Unlawful arrest or detention", Severity.URGENT),
        Category("SUPPRESSION", "Protest Suppression", "📣", "Blocking peaceful assembly", Severity.HIGH),
        Category("CONFISCATION", "Property Confiscation", "📱", "Illegal seizure of property", Severity.HIGH),
        Category("INTIMIDATION", "Intimidation", "😰", "Threats or coercion", Severity.HIGH),
        Category("CENSORSHIP", "Censorship", "🔇", "Speech or media restriction", Severity.MEDIUM),
        Category("OTHER", "Other", "📝", "Other civil rights issue", Severity.MEDIUM)
    )
    
    /**
     * Get categories for a specific use case.
     */
    fun getCategories(useCase: UseCase): List<Category> {
        return when (useCase) {
            UseCase.WOMENS_SAFETY -> womensSafetyCategories
            UseCase.ACCESSIBILITY -> accessibilityCategories
            UseCase.LABOR_RIGHTS -> laborRightsCategories
            UseCase.FACILITIES -> facilitiesCategories
            UseCase.ENVIRONMENTAL -> environmentalCategories
            UseCase.CIVIL_RESISTANCE -> civilResistanceCategories
        }
    }
    
    /**
     * Find a category by ID within a use case.
     */
    fun findCategory(useCase: UseCase, categoryId: String): Category? {
        return getCategories(useCase).find { it.id == categoryId }
    }
    
    /**
     * Get all categories across all use cases.
     */
    fun getAllCategories(): Map<UseCase, List<Category>> {
        return UseCase.entries.associateWith { getCategories(it) }
    }
}

/**
 * Impact message generator based on use case.
 * Provides dynamic messaging for the post creation form.
 */
object ImpactMessages {
    
    fun getWhyThisMatters(useCase: UseCase): String {
        return when (useCase) {
            UseCase.WOMENS_SAFETY -> 
                "Your report helps protect women. When multiple women report the same unsafe area, authorities deploy lights and patrols."
            UseCase.ACCESSIBILITY -> 
                "Your report helps disabled individuals access public spaces. When patterns emerge, organizations allocate budgets to fix barriers."
            UseCase.LABOR_RIGHTS -> 
                "Your report helps protect workers' rights. Documented violations lead to inspections and enforcement actions."
            UseCase.FACILITIES -> 
                "Your report helps maintain safe facilities. Repeated reports prioritize repairs and improvements."
            UseCase.ENVIRONMENTAL -> 
                "Your report helps protect our environment. Documented hazards trigger cleanup and prevention measures."
            UseCase.CIVIL_RESISTANCE -> 
                "Your report documents civil rights issues. Evidence of patterns helps advocacy organizations and legal teams."
        }
    }
    
    fun getSuccessMessage(useCase: UseCase): String {
        return when (useCase) {
            UseCase.WOMENS_SAFETY -> 
                "Women in this area feel safer because of reports like yours. Thank you!"
            UseCase.ACCESSIBILITY -> 
                "Your report helps us create accessible spaces for everyone. Thank you!"
            UseCase.LABOR_RIGHTS -> 
                "Workers' rights are better protected because of reports like yours. Thank you!"
            UseCase.FACILITIES -> 
                "Our community facilities are improved thanks to reports like yours. Thank you!"
            UseCase.ENVIRONMENTAL -> 
                "Our environment is cleaner and safer because of reports like yours. Thank you!"
            UseCase.CIVIL_RESISTANCE -> 
                "Civil rights are better documented thanks to reports like yours. Thank you!"
        }
    }
    
    fun getImpactMetricLabel(useCase: UseCase, count: Int): String {
        return when (useCase) {
            UseCase.WOMENS_SAFETY -> "👥 $count women have reported similar issues in this area"
            UseCase.ACCESSIBILITY -> "👥 $count people have reported accessibility issues here"
            UseCase.LABOR_RIGHTS -> "👥 $count workers have reported issues at this location"
            UseCase.FACILITIES -> "👥 $count people have reported facility problems here"
            UseCase.ENVIRONMENTAL -> "👥 $count people have reported environmental issues here"
            UseCase.CIVIL_RESISTANCE -> "👥 $count people have documented civil rights issues here"
        }
    }
}

/**
 * Sort options for issue lists.
 */
enum class SortOption(val label: String, val icon: String) {
    RECENT("Recent", "🕐"),
    POPULAR("Popular", "👍"),
    URGENT("Urgent", "🚨"),
    NEAREST("Nearest", "📍")
}
