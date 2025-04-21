package com.example.chatapp.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

/**
 * 用户标签实体类
 */
data class UserTagEntity(
    val chatId: String,
    val tagName: String,
    val category: String,
    val confidence: Float,
    val evidence: String = "",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

/**
 * 人设记忆实体类
 */
data class PersonaMemoryEntity(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val content: String,
    val importance: Int = 5,
    val timestamp: Date = Date(),
    val type: String = "character_trait"
)

class ChatDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "chat.db"
        // 增加数据库版本号
        private const val DATABASE_VERSION = 12 // 从11升级到12，添加人设记忆表
        private const val TAG = "ChatDatabaseHelper"  // 添加TAG常量

        // 聊天会话表
        private const val TABLE_CHATS = "chats"
        private const val COLUMN_CHAT_ID = "id"
        private const val COLUMN_CHAT_TITLE = "title"
        private const val COLUMN_CHAT_CREATED_AT = "created_at"
        private const val COLUMN_CHAT_UPDATED_AT = "updated_at"
        private const val COLUMN_CHAT_AI_PERSONA = "ai_persona"
        private const val COLUMN_CHAT_MODEL_TYPE = "model_type"
        private const val COLUMN_CHAT_IS_ARCHIVED = "is_archived"

        // 消息表
        private const val TABLE_MESSAGES = "messages"
        private const val COLUMN_MESSAGE_ID = "id"
        private const val COLUMN_MESSAGE_CHAT_ID = "chat_id"
        private const val COLUMN_MESSAGE_CONTENT = "content"
        private const val COLUMN_MESSAGE_TYPE = "type"
        private const val COLUMN_MESSAGE_TIMESTAMP = "timestamp"
        private const val COLUMN_MESSAGE_IS_ERROR = "is_error"
        private const val COLUMN_MESSAGE_IMAGE_DATA = "image_data"
        private const val COLUMN_MESSAGE_CONTENT_TYPE = "content_type"
        // 添加文档属性列
        private const val COLUMN_MESSAGE_DOCUMENT_SIZE = "document_size"
        private const val COLUMN_MESSAGE_DOCUMENT_TYPE = "document_type"

        // 记忆表
        private const val TABLE_MEMORIES = "memories"
        private const val COLUMN_MEMORY_ID = "id"
        private const val COLUMN_MEMORY_CHAT_ID = "chat_id"
        private const val COLUMN_MEMORY_CONTENT = "content"
        private const val COLUMN_MEMORY_TIMESTAMP = "timestamp"
        private const val COLUMN_MEMORY_START_MESSAGE_ID = "start_message_id"
        private const val COLUMN_MEMORY_END_MESSAGE_ID = "end_message_id"
        private const val COLUMN_MEMORY_CATEGORY = "category"
        private const val COLUMN_MEMORY_IMPORTANCE = "importance"
        private const val COLUMN_MEMORY_KEYWORDS = "keywords"

        // 用户画像表
        private const val TABLE_USER_PROFILES = "user_profiles"
        private const val COLUMN_PROFILE_CHAT_ID = "chat_id"
        private const val COLUMN_PROFILE_CONTENT = "content"
        private const val COLUMN_PROFILE_CREATED_AT = "created_at"
        private const val COLUMN_PROFILE_UPDATED_AT = "updated_at"
        private const val COLUMN_PROFILE_VERSION = "version"

        // 用户标签表
        private const val TABLE_USER_TAGS = "user_tags"
        private const val COLUMN_TAG_ID = "id"
        private const val COLUMN_TAG_CHAT_ID = "chat_id"
        private const val COLUMN_TAG_NAME = "tag_name"
        private const val COLUMN_TAG_CATEGORY = "category"
        private const val COLUMN_TAG_CONFIDENCE = "confidence"
        private const val COLUMN_TAG_EVIDENCE = "evidence"
        private const val COLUMN_TAG_CREATED_AT = "created_at"
        private const val COLUMN_TAG_UPDATED_AT = "updated_at"

        // 闹钟表
        private const val TABLE_ALARMS = "alarms"
        private const val COLUMN_ALARM_ID = "id"
        private const val COLUMN_ALARM_TRIGGER_TIME = "trigger_time"
        private const val COLUMN_ALARM_TITLE = "title"
        private const val COLUMN_ALARM_DESCRIPTION = "description"
        private const val COLUMN_ALARM_IS_ONE_TIME = "is_one_time"
        private const val COLUMN_ALARM_REPEAT_DAYS = "repeat_days"
        private const val COLUMN_ALARM_IS_ACTIVE = "is_active"
        private const val COLUMN_ALARM_CREATED_AT = "created_at"

        // 动态表
        private const val TABLE_MOMENTS = "moments"
        private const val COLUMN_MOMENT_ID = "id"
        private const val COLUMN_MOMENT_CONTENT = "content"
        private const val COLUMN_MOMENT_TYPE = "type"
        private const val COLUMN_MOMENT_TIMESTAMP = "timestamp"
        private const val COLUMN_MOMENT_IMAGE_URI = "image_uri"
        private const val COLUMN_MOMENT_CHAT_ID = "chat_id"
        private const val COLUMN_MOMENT_TITLE = "title"
        private const val COLUMN_MOMENT_IS_DELETED = "is_deleted"

        // 用户反馈表
        private const val TABLE_USER_FEEDBACK = "user_feedback"
        private const val COLUMN_FEEDBACK_ID = "id"
        private const val COLUMN_FEEDBACK_CHAT_ID = "chat_id"
        private const val COLUMN_FEEDBACK_MESSAGE_ID = "message_id"
        private const val COLUMN_FEEDBACK_USER_MESSAGE_ID = "user_message_id"
        private const val COLUMN_FEEDBACK_TYPE = "feedback_type"
        private const val COLUMN_FEEDBACK_CONFIDENCE = "confidence"
        private const val COLUMN_FEEDBACK_CONTENT = "content"
        private const val COLUMN_FEEDBACK_ASPECTS = "aspects"
        private const val COLUMN_FEEDBACK_KEYWORDS = "keywords"
        private const val COLUMN_FEEDBACK_TIMESTAMP = "timestamp"
        private const val COLUMN_FEEDBACK_PROCESSED = "processed"

        // 人设记忆表
        private const val TABLE_PERSONA_MEMORIES = "persona_memories"
        private const val COLUMN_PERSONA_MEMORY_ID = "id"
        private const val COLUMN_PERSONA_MEMORY_CHAT_ID = "chat_id"
        private const val COLUMN_PERSONA_MEMORY_CONTENT = "content"
        private const val COLUMN_PERSONA_MEMORY_TIMESTAMP = "timestamp"
        private const val COLUMN_PERSONA_MEMORY_IMPORTANCE = "importance"
        private const val COLUMN_PERSONA_MEMORY_TYPE = "type"

        // 单例实例
        @Volatile
        private var INSTANCE: ChatDatabaseHelper? = null

        fun getInstance(context: Context): ChatDatabaseHelper {
            return INSTANCE ?: synchronized(this) {
                val instance = ChatDatabaseHelper(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建聊天会话表
        val createChatsTable = """
            CREATE TABLE $TABLE_CHATS (
                $COLUMN_CHAT_ID TEXT PRIMARY KEY,
                $COLUMN_CHAT_TITLE TEXT NOT NULL,
                $COLUMN_CHAT_CREATED_AT INTEGER NOT NULL,
                $COLUMN_CHAT_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_CHAT_AI_PERSONA TEXT NOT NULL DEFAULT '',
                $COLUMN_CHAT_MODEL_TYPE TEXT NOT NULL DEFAULT '',
                $COLUMN_CHAT_IS_ARCHIVED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // 创建消息表
        val createMessagesTable = """
            CREATE TABLE $TABLE_MESSAGES (
                $COLUMN_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_MESSAGE_CHAT_ID TEXT NOT NULL,
                $COLUMN_MESSAGE_CONTENT TEXT NOT NULL,
                $COLUMN_MESSAGE_TYPE INTEGER NOT NULL,
                $COLUMN_MESSAGE_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_MESSAGE_IS_ERROR INTEGER NOT NULL DEFAULT 0,
                $COLUMN_MESSAGE_IMAGE_DATA TEXT,
                $COLUMN_MESSAGE_CONTENT_TYPE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_MESSAGE_DOCUMENT_SIZE TEXT,
                $COLUMN_MESSAGE_DOCUMENT_TYPE TEXT,
                PRIMARY KEY ($COLUMN_MESSAGE_ID, $COLUMN_MESSAGE_CHAT_ID),
                FOREIGN KEY ($COLUMN_MESSAGE_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        // 创建记忆表
        val createMemoriesTable = """
            CREATE TABLE $TABLE_MEMORIES (
                $COLUMN_MEMORY_ID TEXT PRIMARY KEY,
                $COLUMN_MEMORY_CHAT_ID TEXT NOT NULL,
                $COLUMN_MEMORY_CONTENT TEXT NOT NULL,
                $COLUMN_MEMORY_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_MEMORY_START_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_MEMORY_END_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_MEMORY_CATEGORY TEXT DEFAULT '其他',
                $COLUMN_MEMORY_IMPORTANCE INTEGER DEFAULT 5,
                $COLUMN_MEMORY_KEYWORDS TEXT DEFAULT '',
                FOREIGN KEY ($COLUMN_MEMORY_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        // 创建用户画像表
        val createUserProfilesTable = """
            CREATE TABLE $TABLE_USER_PROFILES (
                $COLUMN_PROFILE_CHAT_ID TEXT PRIMARY KEY,
                $COLUMN_PROFILE_CONTENT TEXT NOT NULL,
                $COLUMN_PROFILE_CREATED_AT INTEGER NOT NULL,
                $COLUMN_PROFILE_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_PROFILE_VERSION INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY ($COLUMN_PROFILE_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        // 创建用户标签表
        val createUserTagsTable = """
            CREATE TABLE $TABLE_USER_TAGS (
                $COLUMN_TAG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TAG_CHAT_ID TEXT NOT NULL,
                $COLUMN_TAG_NAME TEXT NOT NULL,
                $COLUMN_TAG_CATEGORY TEXT NOT NULL,
                $COLUMN_TAG_CONFIDENCE REAL NOT NULL,
                $COLUMN_TAG_EVIDENCE TEXT,
                $COLUMN_TAG_CREATED_AT INTEGER NOT NULL,
                $COLUMN_TAG_UPDATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_TAG_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE,
                UNIQUE($COLUMN_TAG_CHAT_ID, $COLUMN_TAG_NAME, $COLUMN_TAG_CATEGORY)
            )
        """.trimIndent()

        // 创建闹钟表
        val createAlarmsTable = """
            CREATE TABLE $TABLE_ALARMS (
                $COLUMN_ALARM_ID TEXT PRIMARY KEY,
                $COLUMN_ALARM_TRIGGER_TIME INTEGER NOT NULL,
                $COLUMN_ALARM_TITLE TEXT NOT NULL,
                $COLUMN_ALARM_DESCRIPTION TEXT NOT NULL DEFAULT '',
                $COLUMN_ALARM_IS_ONE_TIME INTEGER NOT NULL DEFAULT 1,
                $COLUMN_ALARM_REPEAT_DAYS TEXT NOT NULL DEFAULT '',
                $COLUMN_ALARM_IS_ACTIVE INTEGER NOT NULL DEFAULT 1,
                $COLUMN_ALARM_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        // 创建动态表
        val createMomentsTable = """
            CREATE TABLE $TABLE_MOMENTS (
                $COLUMN_MOMENT_ID TEXT PRIMARY KEY,
                $COLUMN_MOMENT_CONTENT TEXT NOT NULL,
                $COLUMN_MOMENT_TYPE INTEGER NOT NULL,
                $COLUMN_MOMENT_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_MOMENT_IMAGE_URI TEXT,
                $COLUMN_MOMENT_CHAT_ID TEXT,
                $COLUMN_MOMENT_TITLE TEXT NOT NULL DEFAULT '',
                $COLUMN_MOMENT_IS_DELETED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        // 创建用户反馈表
        val createFeedbackTable = """
            CREATE TABLE $TABLE_USER_FEEDBACK (
                $COLUMN_FEEDBACK_ID TEXT PRIMARY KEY,
                $COLUMN_FEEDBACK_CHAT_ID TEXT NOT NULL,
                $COLUMN_FEEDBACK_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_FEEDBACK_USER_MESSAGE_ID TEXT NOT NULL,
                $COLUMN_FEEDBACK_TYPE TEXT NOT NULL,
                $COLUMN_FEEDBACK_CONFIDENCE REAL NOT NULL,
                $COLUMN_FEEDBACK_CONTENT TEXT NOT NULL,
                $COLUMN_FEEDBACK_ASPECTS TEXT NOT NULL,
                $COLUMN_FEEDBACK_KEYWORDS TEXT NOT NULL,
                $COLUMN_FEEDBACK_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_FEEDBACK_PROCESSED INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY ($COLUMN_FEEDBACK_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        // 创建人设记忆表
        val createPersonaMemoriesTable = """
            CREATE TABLE $TABLE_PERSONA_MEMORIES (
                $COLUMN_PERSONA_MEMORY_ID TEXT PRIMARY KEY,
                $COLUMN_PERSONA_MEMORY_CHAT_ID TEXT NOT NULL,
                $COLUMN_PERSONA_MEMORY_CONTENT TEXT NOT NULL,
                $COLUMN_PERSONA_MEMORY_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PERSONA_MEMORY_IMPORTANCE INTEGER NOT NULL DEFAULT 5,
                $COLUMN_PERSONA_MEMORY_TYPE TEXT NOT NULL,
                FOREIGN KEY ($COLUMN_PERSONA_MEMORY_CHAT_ID) 
                    REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
            )
        """.trimIndent()

        db.execSQL(createChatsTable)
        db.execSQL(createMessagesTable)
        db.execSQL(createMemoriesTable)
        db.execSQL(createUserProfilesTable)
        db.execSQL(createUserTagsTable)
        db.execSQL(createAlarmsTable)
        db.execSQL(createMomentsTable)
        db.execSQL(createFeedbackTable)
        db.execSQL(createPersonaMemoriesTable)

        // 创建反馈表索引
        db.execSQL("CREATE INDEX idx_feedback_chat_id ON $TABLE_USER_FEEDBACK($COLUMN_FEEDBACK_CHAT_ID)")
        db.execSQL("CREATE INDEX idx_feedback_message_id ON $TABLE_USER_FEEDBACK($COLUMN_FEEDBACK_MESSAGE_ID)")

        // 创建人设记忆表索引
        db.execSQL("CREATE INDEX idx_persona_memory_chat_id ON $TABLE_PERSONA_MEMORIES($COLUMN_PERSONA_MEMORY_CHAT_ID)")

        Log.d(TAG, "数据库创建完成，包含表: $TABLE_CHATS, $TABLE_MESSAGES, $TABLE_MEMORIES, $TABLE_USER_PROFILES, $TABLE_USER_TAGS, $TABLE_ALARMS, $TABLE_MOMENTS, $TABLE_USER_FEEDBACK, $TABLE_PERSONA_MEMORIES")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "数据库升级: 从版本 $oldVersion 到 $newVersion")

        if (oldVersion < 2 && newVersion >= 2) {
            // 如果是从版本1升级到版本2，添加记忆表
            try {
                // 创建记忆表
                val createMemoriesTable = """
                    CREATE TABLE $TABLE_MEMORIES (
                        $COLUMN_MEMORY_ID TEXT PRIMARY KEY,
                        $COLUMN_MEMORY_CHAT_ID TEXT NOT NULL,
                        $COLUMN_MEMORY_CONTENT TEXT NOT NULL,
                        $COLUMN_MEMORY_TIMESTAMP INTEGER NOT NULL,
                        $COLUMN_MEMORY_START_MESSAGE_ID TEXT NOT NULL,
                        $COLUMN_MEMORY_END_MESSAGE_ID TEXT NOT NULL,
                        $COLUMN_MEMORY_CATEGORY TEXT DEFAULT '其他',
                        $COLUMN_MEMORY_IMPORTANCE INTEGER DEFAULT 5,
                        $COLUMN_MEMORY_KEYWORDS TEXT DEFAULT '',
                        FOREIGN KEY ($COLUMN_MEMORY_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                    )
                """.trimIndent()

                db.execSQL(createMemoriesTable)
                Log.d(TAG, "升级成功: 创建了记忆表 $TABLE_MEMORIES")
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 3 && newVersion >= 3) {
            // 从版本2升级到版本3，添加用户画像表
            try {
                // 创建用户画像表
                val createUserProfilesTable = """
                    CREATE TABLE $TABLE_USER_PROFILES (
                        $COLUMN_PROFILE_CHAT_ID TEXT PRIMARY KEY,
                        $COLUMN_PROFILE_CONTENT TEXT NOT NULL,
                        $COLUMN_PROFILE_CREATED_AT INTEGER NOT NULL,
                        $COLUMN_PROFILE_UPDATED_AT INTEGER NOT NULL,
                        $COLUMN_PROFILE_VERSION INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY ($COLUMN_PROFILE_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                    )
                """.trimIndent()

                db.execSQL(createUserProfilesTable)
                Log.d(TAG, "升级成功: 创建了用户画像表 $TABLE_USER_PROFILES")
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 4 && newVersion >= 4) {
            // 从版本3升级到版本4，添加闹钟表
            try {
                // 创建闹钟表
                val createAlarmsTable = """
                    CREATE TABLE $TABLE_ALARMS (
                        $COLUMN_ALARM_ID TEXT PRIMARY KEY,
                        $COLUMN_ALARM_TRIGGER_TIME INTEGER NOT NULL,
                        $COLUMN_ALARM_TITLE TEXT NOT NULL,
                        $COLUMN_ALARM_DESCRIPTION TEXT NOT NULL DEFAULT '',
                        $COLUMN_ALARM_IS_ONE_TIME INTEGER NOT NULL DEFAULT 1,
                        $COLUMN_ALARM_REPEAT_DAYS TEXT NOT NULL DEFAULT '',
                        $COLUMN_ALARM_IS_ACTIVE INTEGER NOT NULL DEFAULT 1,
                        $COLUMN_ALARM_CREATED_AT INTEGER NOT NULL
                    )
                """.trimIndent()

                db.execSQL(createAlarmsTable)
                Log.d(TAG, "升级成功: 创建了闹钟表 $TABLE_ALARMS")
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }

        // 从版本4升级到版本5的逻辑
        if (oldVersion < 5 && newVersion >= 5) {
            try {
                // 检查memories表结构，修复缺失的列
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(TABLE_MEMORIES)
                )
                val tableExists = cursor.count > 0
                cursor.close()

                if (tableExists) {
                    // 检查表结构，确定缺少哪些列
                    val columnsToAdd = mutableListOf<String>()

                    // 查询表结构
                    val columnsQuery = db.rawQuery("PRAGMA table_info($TABLE_MEMORIES)", null)
                    val existingColumns = mutableSetOf<String>()

                    columnsQuery.use { c ->
                        while (c.moveToNext()) {
                            val columnName = c.getString(c.getColumnIndexOrThrow("name"))
                            existingColumns.add(columnName)
                        }
                    }

                    // 检查需要添加的列
                    if (!existingColumns.contains(COLUMN_MEMORY_CATEGORY)) {
                        columnsToAdd.add("$COLUMN_MEMORY_CATEGORY TEXT DEFAULT '其他'")
                    }

                    if (!existingColumns.contains(COLUMN_MEMORY_IMPORTANCE)) {
                        columnsToAdd.add("$COLUMN_MEMORY_IMPORTANCE INTEGER DEFAULT 5")
                    }

                    if (!existingColumns.contains(COLUMN_MEMORY_KEYWORDS)) {
                        columnsToAdd.add("$COLUMN_MEMORY_KEYWORDS TEXT DEFAULT ''")
                    }

                    // 添加缺失的列
                    for (columnDef in columnsToAdd) {
                        try {
                            val alterQuery = "ALTER TABLE $TABLE_MEMORIES ADD COLUMN $columnDef"
                            db.execSQL(alterQuery)
                            Log.d(TAG, "成功添加列: $columnDef")
                        } catch (e: Exception) {
                            Log.e(TAG, "添加列失败: $columnDef, 错误: ${e.message}", e)
                        }
                    }

                    Log.d(TAG, "升级成功: 已修复记忆表结构，添加了${columnsToAdd.size}个列")
                } else {
                    // 表不存在，创建完整的表
                    val createMemoriesTable = """
                        CREATE TABLE $TABLE_MEMORIES (
                            $COLUMN_MEMORY_ID TEXT PRIMARY KEY,
                            $COLUMN_MEMORY_CHAT_ID TEXT NOT NULL,
                            $COLUMN_MEMORY_CONTENT TEXT NOT NULL,
                            $COLUMN_MEMORY_TIMESTAMP INTEGER NOT NULL,
                            $COLUMN_MEMORY_START_MESSAGE_ID TEXT NOT NULL,
                            $COLUMN_MEMORY_END_MESSAGE_ID TEXT NOT NULL,
                            $COLUMN_MEMORY_CATEGORY TEXT DEFAULT '其他',
                            $COLUMN_MEMORY_IMPORTANCE INTEGER DEFAULT 5,
                            $COLUMN_MEMORY_KEYWORDS TEXT DEFAULT '',
                            FOREIGN KEY ($COLUMN_MEMORY_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                        )
                    """.trimIndent()

                    db.execSQL(createMemoriesTable)
                    Log.d(TAG, "升级成功: 创建了新的记忆表，包含所有必要列")
                }

                // 创建动态表
                try {
                    val createMomentsTable = """
                        CREATE TABLE $TABLE_MOMENTS (
                            $COLUMN_MOMENT_ID TEXT PRIMARY KEY,
                            $COLUMN_MOMENT_CONTENT TEXT NOT NULL,
                            $COLUMN_MOMENT_TYPE INTEGER NOT NULL,
                            $COLUMN_MOMENT_TIMESTAMP INTEGER NOT NULL,
                            $COLUMN_MOMENT_IMAGE_URI TEXT,
                            $COLUMN_MOMENT_CHAT_ID TEXT,
                            $COLUMN_MOMENT_TITLE TEXT NOT NULL DEFAULT '',
                            $COLUMN_MOMENT_IS_DELETED INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent()

                    db.execSQL(createMomentsTable)
                    Log.d(TAG, "升级成功: 创建了动态表 $TABLE_MOMENTS")
                } catch (e: Exception) {
                    Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
                }
            } catch (e: Exception) {
                // 捕获并记录所有可能的异常
                Log.e(TAG, "版本5升级过程中发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 6 && newVersion >= 6) {
            // 从版本5升级到版本6，检查并创建动态表
            try {
                // 检查moments表是否存在
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(TABLE_MOMENTS)
                )
                val tableExists = cursor.count > 0
                cursor.close()

                if (!tableExists) {
                    // 创建动态表
                    val createMomentsTable = """
                        CREATE TABLE $TABLE_MOMENTS (
                            $COLUMN_MOMENT_ID TEXT PRIMARY KEY,
                            $COLUMN_MOMENT_CONTENT TEXT NOT NULL,
                            $COLUMN_MOMENT_TYPE INTEGER NOT NULL,
                            $COLUMN_MOMENT_TIMESTAMP INTEGER NOT NULL,
                            $COLUMN_MOMENT_IMAGE_URI TEXT,
                            $COLUMN_MOMENT_CHAT_ID TEXT,
                            $COLUMN_MOMENT_TITLE TEXT NOT NULL DEFAULT '',
                            $COLUMN_MOMENT_IS_DELETED INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent()

                    db.execSQL(createMomentsTable)
                    Log.d(TAG, "升级成功: 创建了动态表 $TABLE_MOMENTS")
                } else {
                    Log.d(TAG, "升级检查: 动态表 $TABLE_MOMENTS 已存在")
                }
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 7 && newVersion >= 7) {
            // 从版本6升级到版本7，添加图片支持
            try {
                // 检查messages表是否存在
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(TABLE_MESSAGES)
                )
                val tableExists = cursor.count > 0
                cursor.close()

                if (tableExists) {
                    // 添加图片数据列
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_IMAGE_DATA TEXT")
                        Log.d(TAG, "升级成功: 消息表添加了图片数据列")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加image_data列失败: ${e.message}", e)
                    }

                    // 添加内容类型列
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_CONTENT_TYPE INTEGER NOT NULL DEFAULT 0")
                        Log.d(TAG, "升级成功: 消息表添加了内容类型列")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加content_type列失败: ${e.message}", e)
                    }

                    Log.d(TAG, "升级检查: 消息表图片支持已添加")
                }
            } catch (e: Exception) {
                Log.e(TAG, "版本7升级过程中发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 8 && newVersion >= 8) {
            // 从版本7升级到版本8，添加用户标签支持
            try {
                // 检查user_tags表是否存在
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(TABLE_USER_TAGS)
                )
                val tableExists = cursor.count > 0
                cursor.close()

                if (!tableExists) {
                    // 创建用户标签表
                    val createUserTagsTable = """
                        CREATE TABLE $TABLE_USER_TAGS (
                            $COLUMN_TAG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                            $COLUMN_TAG_CHAT_ID TEXT NOT NULL,
                            $COLUMN_TAG_NAME TEXT NOT NULL,
                            $COLUMN_TAG_CATEGORY TEXT NOT NULL,
                            $COLUMN_TAG_CONFIDENCE REAL NOT NULL,
                            $COLUMN_TAG_EVIDENCE TEXT,
                            $COLUMN_TAG_CREATED_AT INTEGER NOT NULL,
                            $COLUMN_TAG_UPDATED_AT INTEGER NOT NULL,
                            FOREIGN KEY ($COLUMN_TAG_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE,
                            UNIQUE($COLUMN_TAG_CHAT_ID, $COLUMN_TAG_NAME, $COLUMN_TAG_CATEGORY)
                        )
                    """.trimIndent()

                    db.execSQL(createUserTagsTable)
                    Log.d(TAG, "升级成功: 创建了用户标签表 $TABLE_USER_TAGS")
                } else {
                    Log.d(TAG, "升级检查: 用户标签表 $TABLE_USER_TAGS 已存在")
                }
            } catch (e: Exception) {
                Log.e(TAG, "版本8升级过程中发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 9 && newVersion >= 9) {
            // 从版本8升级到版本9，确保messages表有image_data和content_type列
            try {
                // 检查messages表列结构
                val columnsQuery = db.rawQuery("PRAGMA table_info($TABLE_MESSAGES)", null)
                val existingColumns = mutableSetOf<String>()

                columnsQuery.use { c ->
                    while (c.moveToNext()) {
                        val columnName = c.getString(c.getColumnIndexOrThrow("name"))
                        existingColumns.add(columnName)
                    }
                }

                // 检查并添加缺失的列
                if (!existingColumns.contains(COLUMN_MESSAGE_IMAGE_DATA)) {
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_IMAGE_DATA TEXT")
                        Log.d(TAG, "版本9升级: 添加了image_data列到messages表")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加image_data列失败: ${e.message}", e)
                    }
                }

                if (!existingColumns.contains(COLUMN_MESSAGE_CONTENT_TYPE)) {
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_CONTENT_TYPE INTEGER NOT NULL DEFAULT 0")
                        Log.d(TAG, "版本9升级: 添加了content_type列到messages表")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加content_type列失败: ${e.message}", e)
                    }
                }

                // 检查messages表是否存在主键和外键约束
                val tableInfo = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='$TABLE_MESSAGES'", null)
                var recreateTable = false
                if (tableInfo.moveToFirst()) {
                    val tableCreateSql = tableInfo.getString(0)
                    // 如果表创建SQL不包含primary key或foreign key，需要重建表
                    if (!tableCreateSql.contains("PRIMARY KEY") || !tableCreateSql.contains("FOREIGN KEY")) {
                        recreateTable = true
                    }
                }
                tableInfo.close()

                // 如果需要重建表，备份数据并重建
                if (recreateTable) {
                    Log.d(TAG, "版本9升级: 需要重建messages表以添加主键和外键约束")

                    // 重命名旧表
                    db.execSQL("ALTER TABLE $TABLE_MESSAGES RENAME TO messages_backup")

                    // 创建新表
                    val createMessagesTable = """
                        CREATE TABLE $TABLE_MESSAGES (
                            $COLUMN_MESSAGE_ID TEXT NOT NULL,
                            $COLUMN_MESSAGE_CHAT_ID TEXT NOT NULL,
                            $COLUMN_MESSAGE_CONTENT TEXT NOT NULL,
                            $COLUMN_MESSAGE_TYPE INTEGER NOT NULL,
                            $COLUMN_MESSAGE_TIMESTAMP INTEGER NOT NULL,
                            $COLUMN_MESSAGE_IS_ERROR INTEGER NOT NULL DEFAULT 0,
                            $COLUMN_MESSAGE_IMAGE_DATA TEXT,
                            $COLUMN_MESSAGE_CONTENT_TYPE INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY ($COLUMN_MESSAGE_ID, $COLUMN_MESSAGE_CHAT_ID),
                            FOREIGN KEY ($COLUMN_MESSAGE_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                        )
                    """.trimIndent()
                    db.execSQL(createMessagesTable)

                    // 复制数据
                    db.execSQL("""
                        INSERT INTO $TABLE_MESSAGES 
                        SELECT 
                            id, 
                            chat_id, 
                            content, 
                            type, 
                            timestamp, 
                            is_error, 
                            IFNULL(image_data, NULL) as image_data,
                            IFNULL(content_type, 0) as content_type 
                        FROM messages_backup
                    """)

                    // 删除旧表
                    db.execSQL("DROP TABLE messages_backup")

                    Log.d(TAG, "版本9升级: 成功重建messages表并恢复数据")
                }
            } catch (e: Exception) {
                Log.e(TAG, "版本9升级过程中发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 10 && newVersion >= 10) {
            // 创建用户反馈表
            try {
                val createFeedbackTable = """
                    CREATE TABLE IF NOT EXISTS $TABLE_USER_FEEDBACK (
                        $COLUMN_FEEDBACK_ID TEXT PRIMARY KEY,
                        $COLUMN_FEEDBACK_CHAT_ID TEXT NOT NULL,
                        $COLUMN_FEEDBACK_MESSAGE_ID TEXT NOT NULL,
                        $COLUMN_FEEDBACK_USER_MESSAGE_ID TEXT NOT NULL,
                        $COLUMN_FEEDBACK_TYPE TEXT NOT NULL,
                        $COLUMN_FEEDBACK_CONFIDENCE REAL NOT NULL,
                        $COLUMN_FEEDBACK_CONTENT TEXT NOT NULL,
                        $COLUMN_FEEDBACK_ASPECTS TEXT NOT NULL,
                        $COLUMN_FEEDBACK_KEYWORDS TEXT NOT NULL,
                        $COLUMN_FEEDBACK_TIMESTAMP INTEGER NOT NULL,
                        $COLUMN_FEEDBACK_PROCESSED INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY ($COLUMN_FEEDBACK_CHAT_ID) REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                    )
                """.trimIndent()
                db.execSQL(createFeedbackTable)

                // 创建索引
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_chat_id ON $TABLE_USER_FEEDBACK($COLUMN_FEEDBACK_CHAT_ID)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_feedback_message_id ON $TABLE_USER_FEEDBACK($COLUMN_FEEDBACK_MESSAGE_ID)")

                Log.d(TAG, "升级成功: 创建了用户反馈表")
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 11 && newVersion >= 11) {
            // 从版本10升级到版本11，添加文档属性列
            try {
                // 检查messages表列结构
                val columnsQuery = db.rawQuery("PRAGMA table_info($TABLE_MESSAGES)", null)
                val existingColumns = mutableSetOf<String>()

                columnsQuery.use { c ->
                    while (c.moveToNext()) {
                        val columnName = c.getString(c.getColumnIndexOrThrow("name"))
                        existingColumns.add(columnName)
                    }
                }

                // 检查并添加文档大小列
                if (!existingColumns.contains(COLUMN_MESSAGE_DOCUMENT_SIZE)) {
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_DOCUMENT_SIZE TEXT")
                        Log.d(TAG, "版本11升级: 添加了document_size列到messages表")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加document_size列失败: ${e.message}", e)
                    }
                }

                // 检查并添加文档类型列
                if (!existingColumns.contains(COLUMN_MESSAGE_DOCUMENT_TYPE)) {
                    try {
                        db.execSQL("ALTER TABLE $TABLE_MESSAGES ADD COLUMN $COLUMN_MESSAGE_DOCUMENT_TYPE TEXT")
                        Log.d(TAG, "版本11升级: 添加了document_type列到messages表")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加document_type列失败: ${e.message}", e)
                    }
                }

                Log.d(TAG, "版本11升级成功: 添加了文档属性相关列")
            } catch (e: Exception) {
                Log.e(TAG, "版本11升级过程中发生错误: ${e.message}", e)
            }
        }

        if (oldVersion < 12 && newVersion >= 12) {
            // 从版本11升级到版本12，添加人设记忆表
            try {
                // 创建人设记忆表
                val createPersonaMemoriesTable = """
                    CREATE TABLE $TABLE_PERSONA_MEMORIES (
                        $COLUMN_PERSONA_MEMORY_ID TEXT PRIMARY KEY,
                        $COLUMN_PERSONA_MEMORY_CHAT_ID TEXT NOT NULL,
                        $COLUMN_PERSONA_MEMORY_CONTENT TEXT NOT NULL,
                        $COLUMN_PERSONA_MEMORY_TIMESTAMP INTEGER NOT NULL,
                        $COLUMN_PERSONA_MEMORY_IMPORTANCE INTEGER NOT NULL DEFAULT 5,
                        $COLUMN_PERSONA_MEMORY_TYPE TEXT NOT NULL,
                        FOREIGN KEY ($COLUMN_PERSONA_MEMORY_CHAT_ID) 
                            REFERENCES $TABLE_CHATS($COLUMN_CHAT_ID) ON DELETE CASCADE
                    )
                """.trimIndent()

                db.execSQL(createPersonaMemoriesTable)

                // 创建索引
                db.execSQL("CREATE INDEX idx_persona_memory_chat_id ON $TABLE_PERSONA_MEMORIES($COLUMN_PERSONA_MEMORY_CHAT_ID)")

                Log.d(TAG, "升级成功: 创建了人设记忆表 $TABLE_PERSONA_MEMORIES")
            } catch (e: Exception) {
                Log.e(TAG, "升级数据库时发生错误: ${e.message}", e)
            }
        }
    }

    // --- 聊天会话相关操作 ---

    suspend fun insertChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CHAT_ID, chat.id)
            put(COLUMN_CHAT_TITLE, chat.title)
            put(COLUMN_CHAT_CREATED_AT, chat.createdAt.time)
            put(COLUMN_CHAT_UPDATED_AT, chat.updatedAt.time)
            put(COLUMN_CHAT_AI_PERSONA, chat.aiPersona)
            put(COLUMN_CHAT_MODEL_TYPE, chat.modelType)
            put(COLUMN_CHAT_IS_ARCHIVED, if (chat.isArchived) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_CHATS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun updateChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CHAT_TITLE, chat.title)
            put(COLUMN_CHAT_UPDATED_AT, chat.updatedAt.time)
            put(COLUMN_CHAT_AI_PERSONA, chat.aiPersona)
            put(COLUMN_CHAT_MODEL_TYPE, chat.modelType)
            put(COLUMN_CHAT_IS_ARCHIVED, if (chat.isArchived) 1 else 0)
        }
        db.update(TABLE_CHATS, values, "$COLUMN_CHAT_ID = ?", arrayOf(chat.id))
    }

    suspend fun deleteChat(chat: ChatEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_CHATS, "$COLUMN_CHAT_ID = ?", arrayOf(chat.id))
    }

    fun getAllActiveChats(): Flow<List<ChatEntity>> = flow {
        val chats = mutableListOf<ChatEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHATS,
            null,
            "$COLUMN_CHAT_IS_ARCHIVED = 0",
            null,
            null,
            null,
            "$COLUMN_CHAT_UPDATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                chats.add(cursor.toChatEntity())
            }
        }
        emit(chats)
    }

    fun getAllArchivedChats(): Flow<List<ChatEntity>> = flow {
        val chats = mutableListOf<ChatEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHATS,
            null,
            "$COLUMN_CHAT_IS_ARCHIVED = 1",
            null,
            null,
            null,
            "$COLUMN_CHAT_UPDATED_AT DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                chats.add(cursor.toChatEntity())
            }
        }
        emit(chats)
    }

    suspend fun getChatById(chatId: String): ChatEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CHATS,
            null,
            "$COLUMN_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            null
        )

        var chat: ChatEntity? = null
        cursor.use {
            if (it.moveToFirst()) {
                chat = cursor.toChatEntity()
            }
        }
        chat
    }

    suspend fun updateChatArchiveStatus(chatId: String, isArchived: Boolean) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CHAT_IS_ARCHIVED, if (isArchived) 1 else 0)
            put(COLUMN_CHAT_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(TABLE_CHATS, values, "$COLUMN_CHAT_ID = ?", arrayOf(chatId))
    }

    suspend fun updateChatTitle(chatId: String, title: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CHAT_TITLE, title)
            put(COLUMN_CHAT_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(TABLE_CHATS, values, "$COLUMN_CHAT_ID = ?", arrayOf(chatId))
    }

    // --- 消息相关操作 ---

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase

        // 验证表结构
        ensureTableColumnsExist(db, TABLE_MESSAGES, mapOf(
            COLUMN_MESSAGE_IMAGE_DATA to "TEXT",
            COLUMN_MESSAGE_CONTENT_TYPE to "INTEGER NOT NULL DEFAULT 0",
            COLUMN_MESSAGE_DOCUMENT_SIZE to "TEXT",
            COLUMN_MESSAGE_DOCUMENT_TYPE to "TEXT"
        ))

        val values = ContentValues().apply {
            put(COLUMN_MESSAGE_ID, message.id)
            put(COLUMN_MESSAGE_CHAT_ID, message.chatId)
            put(COLUMN_MESSAGE_CONTENT, message.content)
            put(COLUMN_MESSAGE_TYPE, message.type)
            put(COLUMN_MESSAGE_TIMESTAMP, message.timestamp.time)
            put(COLUMN_MESSAGE_IS_ERROR, if (message.isError) 1 else 0)
            // 添加媒体数据属性
            put(COLUMN_MESSAGE_IMAGE_DATA, message.imageData)
            put(COLUMN_MESSAGE_CONTENT_TYPE, message.contentType)
            // 添加文档属性
            put(COLUMN_MESSAGE_DOCUMENT_SIZE, message.documentSize)
            put(COLUMN_MESSAGE_DOCUMENT_TYPE, message.documentType)
        }

        // 添加日志跟踪数据保存
        Log.d(TAG, "保存消息: id=${message.id}, 类型=${message.contentType}, " +
                "图片数据=${if (message.imageData != null) "存在(${message.imageData.length/1024}KB)" else "无"}, " +
                "文档大小=${message.documentSize ?: "无"}, 文档类型=${message.documentType ?: "无"}")

        db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // 辅助方法：确保表包含所需列
    private fun ensureTableColumnsExist(db: SQLiteDatabase, tableName: String, columns: Map<String, String>) {
        try {
            val columnsQuery = db.rawQuery("PRAGMA table_info($tableName)", null)
            val existingColumns = mutableSetOf<String>()

            columnsQuery.use { c ->
                while (c.moveToNext()) {
                    val columnName = c.getString(c.getColumnIndexOrThrow("name"))
                    existingColumns.add(columnName)
                }
            }

            // 检查并添加缺失的列
            for ((columnName, columnType) in columns) {
                if (!existingColumns.contains(columnName)) {
                    try {
                        db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType")
                        Log.d(TAG, "添加列: $columnName 到表 $tableName")
                    } catch (e: Exception) {
                        Log.e(TAG, "添加列失败: $columnName, 错误: ${e.message}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查表结构时发生错误: ${e.message}", e)
        }
    }

    suspend fun insertMessages(messages: List<MessageEntity>) = withContext(Dispatchers.IO) {
        val db = writableDatabase

        // 验证表结构
        ensureTableColumnsExist(db, TABLE_MESSAGES, mapOf(
            COLUMN_MESSAGE_IMAGE_DATA to "TEXT",
            COLUMN_MESSAGE_CONTENT_TYPE to "INTEGER NOT NULL DEFAULT 0",
            COLUMN_MESSAGE_DOCUMENT_SIZE to "TEXT",
            COLUMN_MESSAGE_DOCUMENT_TYPE to "TEXT"
        ))

        db.beginTransaction()
        try {
            for (message in messages) {
                val values = ContentValues().apply {
                    put(COLUMN_MESSAGE_ID, message.id)
                    put(COLUMN_MESSAGE_CHAT_ID, message.chatId)
                    put(COLUMN_MESSAGE_CONTENT, message.content)
                    put(COLUMN_MESSAGE_TYPE, message.type)
                    put(COLUMN_MESSAGE_TIMESTAMP, message.timestamp.time)
                    put(COLUMN_MESSAGE_IS_ERROR, if (message.isError) 1 else 0)
                    // 添加图片数据
                    put(COLUMN_MESSAGE_IMAGE_DATA, message.imageData)
                    put(COLUMN_MESSAGE_CONTENT_TYPE, message.contentType)
                    // 添加文档属性
                    put(COLUMN_MESSAGE_DOCUMENT_SIZE, message.documentSize)
                    put(COLUMN_MESSAGE_DOCUMENT_TYPE, message.documentType)
                }
                db.insertWithOnConflict(TABLE_MESSAGES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 从数据库中删除指定消息
     */
    fun deleteMessage(message: MessageEntity) {
        val db = writableDatabase
        try {
            // 使用消息ID和聊天ID作为条件，确保删除正确的消息
            val result = db.delete(
                "messages",  // 表名
                "id = ? AND chat_id = ?",  // WHERE子句
                arrayOf(message.id, message.chatId)  // WHERE参数
            )
            Log.d(TAG, "已从数据库删除消息: ${message.id}, 结果: $result")
        } catch (e: Exception) {
            Log.e(TAG, "从数据库删除消息失败: ${e.message}", e)
            throw e
        }
    }

    fun getMessagesForChat(chatId: String): Flow<List<MessageEntity>> = flow {
        val messages = mutableListOf<MessageEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_MESSAGE_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_MESSAGE_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                messages.add(cursor.toMessageEntity())
            }
        }
        emit(messages)
    }

    suspend fun getMessagesForChatList(chatId: String): List<MessageEntity> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_MESSAGE_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_MESSAGE_TIMESTAMP ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                messages.add(cursor.toMessageEntity())
            }
        }
        messages
    }

    suspend fun deleteAllMessagesForChat(chatId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MESSAGES, "$COLUMN_MESSAGE_CHAT_ID = ?", arrayOf(chatId))
    }

    suspend fun getMessageById(messageId: String, chatId: String): MessageEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES,
            null,
            "$COLUMN_MESSAGE_ID = ? AND $COLUMN_MESSAGE_CHAT_ID = ?",
            arrayOf(messageId, chatId),
            null,
            null,
            null
        )

        var message: MessageEntity? = null
        cursor.use {
            if (it.moveToFirst()) {
                message = cursor.toMessageEntity()
            }
        }
        message
    }

    // 分页查询消息
    fun getMessagesForChatPaged(chatId: String, limit: Int, offset: Int): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        val db = this.readableDatabase

        // 修改SQL查询，包含所有必要的列
        val query = """
        SELECT id, chat_id, content, type, timestamp, is_error,
               image_data, content_type, document_size, document_type
        FROM $TABLE_MESSAGES
        WHERE chat_id = ?
        ORDER BY timestamp ASC
        LIMIT ? OFFSET ?
    """

        val cursor = db.rawQuery(
            query,
            arrayOf(chatId, limit.toString(), offset.toString())
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow("type"))
            val timestampLong = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
            val timestamp = Date(timestampLong)
            val isError = cursor.getInt(cursor.getColumnIndexOrThrow("is_error")) == 1

            // 获取图片数据
            val imageData = cursor.getStringOrNull(cursor.getColumnIndex("image_data"))

            // 获取内容类型
            val contentType = cursor.getIntOrDefault(cursor.getColumnIndex("content_type"), 0)

            // 获取文档属性
            val documentSize = cursor.getStringOrNull(cursor.getColumnIndex("document_size"))
            val documentType = cursor.getStringOrNull(cursor.getColumnIndex("document_type"))

            val message = MessageEntity(
                id = id,
                chatId = chatId,
                content = content,
                type = type,
                timestamp = timestamp,
                isError = isError,
                imageData = imageData,
                contentType = contentType,
                documentSize = documentSize,
                documentType = documentType
            )

            messages.add(message)
        }

        cursor.close()
        return messages
    }

    // 获取特定消息之前的消息
    fun getMessagesBefore(chatId: String, messageId: String, limit: Int): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        val db = this.readableDatabase

        // 先获取目标消息的timestamp
        val timeQuery = "SELECT timestamp FROM $TABLE_MESSAGES WHERE chat_id = ? AND id = ?"
        val timeCursor = db.rawQuery(timeQuery, arrayOf(chatId, messageId))

        if (!timeCursor.moveToFirst()) {
            timeCursor.close()
            return emptyList()
        }

        val targetTimestamp = timeCursor.getLong(0)
        timeCursor.close()

        // 获取目标消息之前的消息
        val query = """
        SELECT id, chat_id, content, type, timestamp, is_error,
               image_data, content_type, document_size, document_type
        FROM $TABLE_MESSAGES
        WHERE chat_id = ? AND timestamp < ?
        ORDER BY timestamp DESC
        LIMIT ?
    """

        val cursor = db.rawQuery(
            query,
            arrayOf(chatId, targetTimestamp.toString(), limit.toString())
        )

        val tempList = mutableListOf<MessageEntity>()

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow("type"))
            val timestampLong = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
            val timestamp = Date(timestampLong)
            val isError = cursor.getInt(cursor.getColumnIndexOrThrow("is_error")) == 1

            // 获取图片数据
            val imageData = cursor.getStringOrNull(cursor.getColumnIndex("image_data"))

            // 获取内容类型
            val contentType = cursor.getIntOrDefault(cursor.getColumnIndex("content_type"), 0)

            // 获取文档属性
            val documentSize = cursor.getStringOrNull(cursor.getColumnIndex("document_size"))
            val documentType = cursor.getStringOrNull(cursor.getColumnIndex("document_type"))

            val message = MessageEntity(
                id = id,
                chatId = chatId,
                content = content,
                type = type,
                timestamp = timestamp,
                isError = isError,
                imageData = imageData,
                contentType = contentType,
                documentSize = documentSize,
                documentType = documentType
            )

            tempList.add(message)
        }

        cursor.close()

        // 反转列表
        return tempList.reversed()
    }

    // 获取特定消息之后的消息
    fun getMessagesAfter(chatId: String, messageId: String, limit: Int): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        val db = this.readableDatabase

        // 先获取目标消息的timestamp
        val timeQuery = "SELECT timestamp FROM $TABLE_MESSAGES WHERE chat_id = ? AND id = ?"
        val timeCursor = db.rawQuery(timeQuery, arrayOf(chatId, messageId))

        if (!timeCursor.moveToFirst()) {
            timeCursor.close()
            return emptyList()
        }

        val targetTimestamp = timeCursor.getLong(0)
        timeCursor.close()

        // 获取目标消息之后的消息
        val query = """
        SELECT id, chat_id, content, type, timestamp, is_error,
               image_data, content_type, document_size, document_type
        FROM $TABLE_MESSAGES
        WHERE chat_id = ? AND timestamp > ?
        ORDER BY timestamp ASC
        LIMIT ?
    """

        val cursor = db.rawQuery(
            query,
            arrayOf(chatId, targetTimestamp.toString(), limit.toString())
        )

        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
            val content = cursor.getString(cursor.getColumnIndexOrThrow("content"))
            val type = cursor.getInt(cursor.getColumnIndexOrThrow("type"))
            val timestampLong = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
            val timestamp = Date(timestampLong)
            val isError = cursor.getInt(cursor.getColumnIndexOrThrow("is_error")) == 1

            // 获取图片数据
            val imageData = cursor.getStringOrNull(cursor.getColumnIndex("image_data"))

            // 获取内容类型
            val contentType = cursor.getIntOrDefault(cursor.getColumnIndex("content_type"), 0)

            // 获取文档属性
            val documentSize = cursor.getStringOrNull(cursor.getColumnIndex("document_size"))
            val documentType = cursor.getStringOrNull(cursor.getColumnIndex("document_type"))

            val message = MessageEntity(
                id = id,
                chatId = chatId,
                content = content,
                type = type,
                timestamp = timestamp,
                isError = isError,
                imageData = imageData,
                contentType = contentType,
                documentSize = documentSize,
                documentType = documentType
            )

            messages.add(message)
        }

        cursor.close()
        return messages
    }

    // --- 记忆相关操作 ---

    suspend fun insertMemory(memory: MemoryEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MEMORY_ID, memory.id)
            put(COLUMN_MEMORY_CHAT_ID, memory.chatId)
            put(COLUMN_MEMORY_CONTENT, memory.content)
            put(COLUMN_MEMORY_TIMESTAMP, memory.timestamp.time)
            put(COLUMN_MEMORY_START_MESSAGE_ID, memory.startMessageId)
            put(COLUMN_MEMORY_END_MESSAGE_ID, memory.endMessageId)
            put(COLUMN_MEMORY_CATEGORY, memory.category)
            put(COLUMN_MEMORY_IMPORTANCE, memory.importance)
            put(COLUMN_MEMORY_KEYWORDS, memory.keywords.joinToString(","))
        }
        db.insertWithOnConflict(TABLE_MEMORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMemoriesForChat(chatId: String): Flow<List<MemoryEntity>> = flow {
        val memories = mutableListOf<MemoryEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEMORIES,
            null,
            "$COLUMN_MEMORY_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_MEMORY_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                memories.add(cursor.toMemoryEntitySafely())
            }
        }
        emit(memories)
    }

    /**
     * 获取特定聊天的所有记忆
     */
    suspend fun getMemoriesForChatAsList(chatId: String): List<MemoryEntity> = withContext(Dispatchers.IO) {
        val memories = mutableListOf<MemoryEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MEMORIES,
            null,
            "$COLUMN_MEMORY_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_MEMORY_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                memories.add(cursor.toMemoryEntitySafely())
            }
        }
        memories
    }

    /**
     * 删除特定记忆
     */
    suspend fun deleteMemory(memoryId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MEMORIES, "$COLUMN_MEMORY_ID = ?", arrayOf(memoryId))
    }

    suspend fun deleteAllMemoriesForChat(chatId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MEMORIES, "$COLUMN_MEMORY_CHAT_ID = ?", arrayOf(chatId))
    }

    // --- 用户画像相关操作 ---

    /**
     * 保存用户画像
     */
    suspend fun saveUserProfile(profile: UserProfileEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        try {
            db.beginTransaction()

            val values = ContentValues().apply {
                put(COLUMN_PROFILE_CHAT_ID, profile.chatId)
                put(COLUMN_PROFILE_CONTENT, profile.content)
                put(COLUMN_PROFILE_CREATED_AT, profile.createdAt.time)
                put(COLUMN_PROFILE_UPDATED_AT, profile.updatedAt.time)
                put(COLUMN_PROFILE_VERSION, profile.version)
            }

            // 检查记录是否已存在
            val cursor = db.query(
                TABLE_USER_PROFILES,
                arrayOf(COLUMN_PROFILE_CHAT_ID, COLUMN_PROFILE_VERSION, COLUMN_PROFILE_CREATED_AT),
                "$COLUMN_PROFILE_CHAT_ID = ?",
                arrayOf(profile.chatId),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                // 已存在，更新记录
                val currentVersion = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_VERSION))
                val createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_CREATED_AT))
                values.put(COLUMN_PROFILE_VERSION, currentVersion + 1)
                values.put(COLUMN_PROFILE_CREATED_AT, createdAt)

                db.update(
                    TABLE_USER_PROFILES,
                    values,
                    "$COLUMN_PROFILE_CHAT_ID = ?",
                    arrayOf(profile.chatId)
                )

                Log.d(TAG, "更新用户画像: chatId=${profile.chatId}, 版本=${currentVersion + 1}")
            } else {
                // 不存在，插入新记录
                db.insert(TABLE_USER_PROFILES, null, values)
                Log.d(TAG, "创建新用户画像: chatId=${profile.chatId}, 版本=${profile.version}")
            }

            cursor.close()
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "保存用户画像失败: ${e.message}", e)
        } finally {
            if (db.inTransaction()) {
                db.endTransaction()
            }
        }
    }

    /**
     * 获取用户画像
     */
    suspend fun getUserProfile(chatId: String): UserProfileEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        var profile: UserProfileEntity? = null

        try {
            val cursor = db.query(
                TABLE_USER_PROFILES,
                null,
                "$COLUMN_PROFILE_CHAT_ID = ?",
                arrayOf(chatId),
                null, null, null
            )

            if (cursor.moveToFirst()) {
                val content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_CONTENT))
                val createdAt = Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_CREATED_AT)))
                val updatedAt = Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_UPDATED_AT)))
                val version = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROFILE_VERSION))

                profile = UserProfileEntity(
                    chatId = chatId,
                    content = content,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = version
                )

                Log.d(TAG, "加载用户画像: chatId=$chatId, 版本=$version")
            }

            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "获取用户画像失败: ${e.message}", e)
        }

        profile
    }

    /**
     * 删除用户画像
     */
    suspend fun deleteUserProfile(chatId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        try {
            db.delete(
                TABLE_USER_PROFILES,
                "$COLUMN_PROFILE_CHAT_ID = ?",
                arrayOf(chatId)
            )
            Log.d(TAG, "删除用户画像: chatId=$chatId")
        } catch (e: Exception) {
            Log.e(TAG, "删除用户画像失败: ${e.message}", e)
        }
    }

    // --- 用户标签相关操作 ---

    /**
     * 保存用户标签
     */
    fun saveUserTag(tag: UserTagEntity) {
        val db = writableDatabase

        val values = ContentValues().apply {
            put(COLUMN_TAG_CHAT_ID, tag.chatId)
            put(COLUMN_TAG_NAME, tag.tagName)
            put(COLUMN_TAG_CATEGORY, tag.category)
            put(COLUMN_TAG_CONFIDENCE, tag.confidence)
            put(COLUMN_TAG_EVIDENCE, tag.evidence)
            put(COLUMN_TAG_CREATED_AT, tag.createdAt.time)
            put(COLUMN_TAG_UPDATED_AT, tag.updatedAt.time)
        }

        // 尝试更新，如果不存在则插入
        val rowsAffected = db.update(
            TABLE_USER_TAGS,
            values,
            "$COLUMN_TAG_CHAT_ID = ? AND $COLUMN_TAG_NAME = ? AND $COLUMN_TAG_CATEGORY = ?",
            arrayOf(tag.chatId, tag.tagName, tag.category)
        )

        if (rowsAffected == 0) {
            db.insert(TABLE_USER_TAGS, null, values)
            Log.d(TAG, "创建新用户标签: chatId=${tag.chatId}, 标签=${tag.tagName}(${tag.category}), 置信度=${tag.confidence}")
        } else {
            Log.d(TAG, "更新用户标签: chatId=${tag.chatId}, 标签=${tag.tagName}(${tag.category}), 置信度=${tag.confidence}")
        }
    }

    /**
     * 获取用户标签
     */
    fun getUserTags(chatId: String): List<UserTagEntity> {
        val tags = mutableListOf<UserTagEntity>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_USER_TAGS,
            null,
            "$COLUMN_TAG_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_TAG_CONFIDENCE DESC"
        )

        cursor.use {
            val chatIdIndex = it.getColumnIndexOrThrow(COLUMN_TAG_CHAT_ID)
            val tagNameIndex = it.getColumnIndexOrThrow(COLUMN_TAG_NAME)
            val categoryIndex = it.getColumnIndexOrThrow(COLUMN_TAG_CATEGORY)
            val confidenceIndex = it.getColumnIndexOrThrow(COLUMN_TAG_CONFIDENCE)
            val evidenceIndex = it.getColumnIndexOrThrow(COLUMN_TAG_EVIDENCE)
            val createdAtIndex = it.getColumnIndexOrThrow(COLUMN_TAG_CREATED_AT)
            val updatedAtIndex = it.getColumnIndexOrThrow(COLUMN_TAG_UPDATED_AT)

            while (it.moveToNext()) {
                val tagEntity = UserTagEntity(
                    chatId = it.getString(chatIdIndex),
                    tagName = it.getString(tagNameIndex),
                    category = it.getString(categoryIndex),
                    confidence = it.getFloat(confidenceIndex),
                    evidence = it.getString(evidenceIndex),
                    createdAt = Date(it.getLong(createdAtIndex)),
                    updatedAt = Date(it.getLong(updatedAtIndex))
                )
                tags.add(tagEntity)
            }
        }

        Log.d(TAG, "加载用户标签: chatId=$chatId, 数量=${tags.size}")
        return tags
    }

    /**
     * 删除用户标签
     */
    fun deleteUserTags(chatId: String) {
        val db = writableDatabase
        val count = db.delete(TABLE_USER_TAGS, "$COLUMN_TAG_CHAT_ID = ?", arrayOf(chatId))
        Log.d(TAG, "删除用户标签: chatId=$chatId, 数量=$count")
    }

    // --- 人设记忆相关操作 ---

    /**
     * 插入人设记忆
     */
    suspend fun insertPersonaMemory(memory: PersonaMemoryEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_PERSONA_MEMORY_ID, memory.id)
            put(COLUMN_PERSONA_MEMORY_CHAT_ID, memory.chatId)
            put(COLUMN_PERSONA_MEMORY_CONTENT, memory.content)
            put(COLUMN_PERSONA_MEMORY_TIMESTAMP, memory.timestamp.time)
            put(COLUMN_PERSONA_MEMORY_IMPORTANCE, memory.importance)
            put(COLUMN_PERSONA_MEMORY_TYPE, memory.type)
        }
        db.insertWithOnConflict(TABLE_PERSONA_MEMORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)

        Log.d(TAG, "插入人设记忆: chatId=${memory.chatId}, 内容=${memory.content.take(50)}...")
    }

    /**
     * 获取特定聊天的人设记忆（按重要性排序）
     */
    suspend fun getPersonaMemoriesForChat(chatId: String): List<PersonaMemoryEntity> = withContext(Dispatchers.IO) {
        val memories = mutableListOf<PersonaMemoryEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PERSONA_MEMORIES,
            null,
            "$COLUMN_PERSONA_MEMORY_CHAT_ID = ?",
            arrayOf(chatId),
            null,
            null,
            "$COLUMN_PERSONA_MEMORY_IMPORTANCE DESC, $COLUMN_PERSONA_MEMORY_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                memories.add(cursor.toPersonaMemoryEntity())
            }
        }

        Log.d(TAG, "加载人设记忆: chatId=$chatId, 数量=${memories.size}")
        memories
    }

    /**
     * 删除特定人设记忆
     */
    suspend fun deletePersonaMemory(memoryId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(
            TABLE_PERSONA_MEMORIES,
            "$COLUMN_PERSONA_MEMORY_ID = ?",
            arrayOf(memoryId)
        )

        Log.d(TAG, "删除人设记忆: id=$memoryId")
    }

    /**
     * 删除特定聊天的所有人设记忆
     */
    suspend fun deleteAllPersonaMemoriesForChat(chatId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val count = db.delete(
            TABLE_PERSONA_MEMORIES,
            "$COLUMN_PERSONA_MEMORY_CHAT_ID = ?",
            arrayOf(chatId)
        )

        Log.d(TAG, "删除聊天人设记忆: chatId=$chatId, 数量=$count")
    }

    // --- 用户反馈相关操作 ---

    /**
     * 保存用户反馈
     */
    suspend fun saveFeedback(feedback: FeedbackEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase

            val values = ContentValues().apply {
                put(COLUMN_FEEDBACK_ID, feedback.id)
                put(COLUMN_FEEDBACK_CHAT_ID, feedback.chatId)
                put(COLUMN_FEEDBACK_MESSAGE_ID, feedback.messageId)
                put(COLUMN_FEEDBACK_USER_MESSAGE_ID, feedback.userMessageId)
                put(COLUMN_FEEDBACK_TYPE, feedback.feedbackType)
                put(COLUMN_FEEDBACK_CONFIDENCE, feedback.confidence)
                put(COLUMN_FEEDBACK_CONTENT, feedback.content)
                put(COLUMN_FEEDBACK_ASPECTS, feedback.aspects)
                put(COLUMN_FEEDBACK_KEYWORDS, feedback.keywords)
                put(COLUMN_FEEDBACK_TIMESTAMP, feedback.timestamp.time)
                put(COLUMN_FEEDBACK_PROCESSED, if (feedback.processed) 1 else 0)
            }

            val result = db.insertWithOnConflict(
                TABLE_USER_FEEDBACK,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
            )

            Log.d(TAG, "保存反馈: ${feedback.feedbackType}, 结果: ${result != -1L}")
            return@withContext result != -1L
        } catch (e: Exception) {
            Log.e(TAG, "保存反馈失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 获取特定聊天的所有反馈
     */
    suspend fun getFeedbackForChat(chatId: String): List<FeedbackEntity> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FeedbackEntity>()
        try {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_USER_FEEDBACK,
                null,
                "$COLUMN_FEEDBACK_CHAT_ID = ?",
                arrayOf(chatId),
                null,
                null,
                "$COLUMN_FEEDBACK_TIMESTAMP DESC"
            )

            cursor.use {
                while (it.moveToNext()) {
                    results.add(it.toFeedbackEntity())
                }
            }

            Log.d(TAG, "获取聊天反馈: chatId=$chatId, 数量=${results.size}")
            return@withContext results
        } catch (e: Exception) {
            Log.e(TAG, "获取聊天反馈失败: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * 从Cursor转换为FeedbackEntity
     */
    private fun Cursor.toFeedbackEntity(): FeedbackEntity {
        return FeedbackEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_ID)),
            chatId = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_CHAT_ID)),
            messageId = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_MESSAGE_ID)),
            userMessageId = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_USER_MESSAGE_ID)),
            feedbackType = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_TYPE)),
            confidence = getFloat(getColumnIndexOrThrow(COLUMN_FEEDBACK_CONFIDENCE)),
            content = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_CONTENT)),
            aspects = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_ASPECTS)),
            keywords = getString(getColumnIndexOrThrow(COLUMN_FEEDBACK_KEYWORDS)),
            timestamp = Date(getLong(getColumnIndexOrThrow(COLUMN_FEEDBACK_TIMESTAMP))),
            processed = getInt(getColumnIndexOrThrow(COLUMN_FEEDBACK_PROCESSED)) == 1
        )
    }

    /**
     * 将Cursor转换为PersonaMemoryEntity
     */
    private fun Cursor.toPersonaMemoryEntity(): PersonaMemoryEntity {
        return PersonaMemoryEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_ID)),
            chatId = getString(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_CHAT_ID)),
            content = getString(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_CONTENT)),
            importance = getInt(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_IMPORTANCE)),
            timestamp = Date(getLong(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_TIMESTAMP))),
            type = getString(getColumnIndexOrThrow(COLUMN_PERSONA_MEMORY_TYPE))
        )
    }

    // --- 闹钟相关操作 ---

    /**
     * 插入闹钟
     */
    suspend fun insertAlarm(alarm: AlarmEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ALARM_ID, alarm.id)
            put(COLUMN_ALARM_TRIGGER_TIME, alarm.triggerTime)
            put(COLUMN_ALARM_TITLE, alarm.title)
            put(COLUMN_ALARM_DESCRIPTION, alarm.description)
            put(COLUMN_ALARM_IS_ONE_TIME, if (alarm.isOneTime) 1 else 0)
            put(COLUMN_ALARM_REPEAT_DAYS, alarm.repeatDays)
            put(COLUMN_ALARM_IS_ACTIVE, if (alarm.isActive) 1 else 0)
            put(COLUMN_ALARM_CREATED_AT, alarm.createdAt)
        }
        db.insertWithOnConflict(TABLE_ALARMS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * 获取所有闹钟
     */
    suspend fun getAllAlarms(): List<AlarmEntity> = withContext(Dispatchers.IO) {
        val alarms = mutableListOf<AlarmEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ALARMS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_ALARM_TRIGGER_TIME ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                alarms.add(it.toAlarmEntity())
            }
        }
        alarms
    }

    /**
     * 获取所有激活的闹钟
     */
    suspend fun getAllActiveAlarms(): List<AlarmEntity> = withContext(Dispatchers.IO) {
        val alarms = mutableListOf<AlarmEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ALARMS,
            null,
            "$COLUMN_ALARM_IS_ACTIVE = 1",
            null,
            null,
            null,
            "$COLUMN_ALARM_TRIGGER_TIME ASC"
        )

        cursor.use {
            while (it.moveToNext()) {
                alarms.add(it.toAlarmEntity())
            }
        }
        alarms
    }

    /**
     * 获取闹钟通过ID
     */
    suspend fun getAlarmById(alarmId: String): AlarmEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ALARMS,
            null,
            "$COLUMN_ALARM_ID = ?",
            arrayOf(alarmId),
            null,
            null,
            null
        )

        var alarm: AlarmEntity? = null
        cursor.use {
            if (it.moveToFirst()) {
                alarm = it.toAlarmEntity()
            }
        }
        alarm
    }

    /**
     * 更新闹钟激活状态
     */
    suspend fun updateAlarmActiveState(alarmId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_ALARM_IS_ACTIVE, if (isActive) 1 else 0)
        }
        db.update(TABLE_ALARMS, values, "$COLUMN_ALARM_ID = ?", arrayOf(alarmId))
    }

    /**
     * 删除闹钟
     */
    suspend fun deleteAlarm(alarmId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_ALARMS, "$COLUMN_ALARM_ID = ?", arrayOf(alarmId))
    }

    /**
     * 删除所有闹钟
     */
    suspend fun deleteAllAlarms() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_ALARMS, null, null)
    }

    // --- 动态相关操作 ---

    /**
     * 插入动态
     */
    suspend fun insertMoment(moment: MomentEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MOMENT_ID, moment.id)
            put(COLUMN_MOMENT_CONTENT, moment.content)
            put(COLUMN_MOMENT_TYPE, moment.type)
            put(COLUMN_MOMENT_TIMESTAMP, moment.timestamp.time)
            put(COLUMN_MOMENT_IMAGE_URI, moment.imageUri)
            put(COLUMN_MOMENT_CHAT_ID, moment.chatId)
            put(COLUMN_MOMENT_TITLE, moment.title)
            put(COLUMN_MOMENT_IS_DELETED, if (moment.isDeleted) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_MOMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * 获取所有动态
     */
    suspend fun getAllMoments(): List<MomentEntity> = withContext(Dispatchers.IO) {
        val moments = mutableListOf<MomentEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MOMENTS,
            null,
            "$COLUMN_MOMENT_IS_DELETED = 0",
            null,
            null,
            null,
            "$COLUMN_MOMENT_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                moments.add(it.toMomentEntity())
            }
        }
        moments
    }

    /**
     * 通过ID获取动态
     */
    suspend fun getMomentById(momentId: String): MomentEntity? = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MOMENTS,
            null,
            "$COLUMN_MOMENT_ID = ? AND $COLUMN_MOMENT_IS_DELETED = 0",
            arrayOf(momentId),
            null,
            null,
            null
        )

        var moment: MomentEntity? = null
        cursor.use {
            if (it.moveToFirst()) {
                moment = it.toMomentEntity()
            }
        }
        moment
    }

    /**
     * 更新动态
     */
    suspend fun updateMoment(moment: MomentEntity) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MOMENT_CONTENT, moment.content)
            put(COLUMN_MOMENT_TYPE, moment.type)
            put(COLUMN_MOMENT_IMAGE_URI, moment.imageUri)
            put(COLUMN_MOMENT_CHAT_ID, moment.chatId)
            put(COLUMN_MOMENT_TITLE, moment.title)
            put(COLUMN_MOMENT_IS_DELETED, if (moment.isDeleted) 1 else 0)
        }
        db.update(TABLE_MOMENTS, values, "$COLUMN_MOMENT_ID = ?", arrayOf(moment.id))
    }

    /**
     * 软删除动态
     */
    suspend fun softDeleteMoment(momentId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_MOMENT_IS_DELETED, 1)
        }
        db.update(TABLE_MOMENTS, values, "$COLUMN_MOMENT_ID = ?", arrayOf(momentId))
    }

    /**
     * 硬删除动态
     */
    suspend fun hardDeleteMoment(momentId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MOMENTS, "$COLUMN_MOMENT_ID = ?", arrayOf(momentId))
    }

    /**
     * 获取AI生成的动态
     */
    suspend fun getAIGeneratedMoments(): List<MomentEntity> = withContext(Dispatchers.IO) {
        val moments = mutableListOf<MomentEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MOMENTS,
            null,
            "$COLUMN_MOMENT_TYPE = 1 AND $COLUMN_MOMENT_IS_DELETED = 0",
            null,
            null,
            null,
            "$COLUMN_MOMENT_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                moments.add(it.toMomentEntity())
            }
        }
        moments
    }

    /**
     * 获取用户上传的动态
     */
    suspend fun getUserUploadedMoments(): List<MomentEntity> = withContext(Dispatchers.IO) {
        val moments = mutableListOf<MomentEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_MOMENTS,
            null,
            "$COLUMN_MOMENT_TYPE = 0 AND $COLUMN_MOMENT_IS_DELETED = 0",
            null,
            null,
            null,
            "$COLUMN_MOMENT_TIMESTAMP DESC"
        )

        cursor.use {
            while (it.moveToNext()) {
                moments.add(it.toMomentEntity())
            }
        }
        moments
    }

    // --- 联合操作 ---

    suspend fun deleteChatWithMessages(chatId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_USER_TAGS, "$COLUMN_TAG_CHAT_ID = ?", arrayOf(chatId)) // 删除用户标签
            db.delete(TABLE_USER_PROFILES, "$COLUMN_PROFILE_CHAT_ID = ?", arrayOf(chatId)) // 删除用户画像
            db.delete(TABLE_MEMORIES, "$COLUMN_MEMORY_CHAT_ID = ?", arrayOf(chatId)) // 删除记忆
            db.delete(TABLE_MESSAGES, "$COLUMN_MESSAGE_CHAT_ID = ?", arrayOf(chatId)) // 删除消息
            db.delete(TABLE_USER_FEEDBACK, "$COLUMN_FEEDBACK_CHAT_ID = ?", arrayOf(chatId)) // 删除用户反馈
            db.delete(TABLE_PERSONA_MEMORIES, "$COLUMN_PERSONA_MEMORY_CHAT_ID = ?", arrayOf(chatId)) // 删除人设记忆
            db.delete(TABLE_CHATS, "$COLUMN_CHAT_ID = ?", arrayOf(chatId)) // 删除会话
            db.setTransactionSuccessful()
            Log.d(TAG, "删除会话及相关数据: chatId=$chatId")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getMessageCountForChat(chatId: String): Int = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MESSAGES WHERE $COLUMN_MESSAGE_CHAT_ID = ?",
            arrayOf(chatId)
        )

        var count = 0
        cursor.use {
            if (it.moveToFirst()) {
                count = it.getInt(0)
            }
        }
        count
    }

    suspend fun searchMessages(query: String): List<MessageWithChat> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MessageWithChat>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT m.*, c.title, c.created_at as chat_created_at, c.updated_at as chat_updated_at, 
                   c.ai_persona, c.model_type, c.is_archived 
            FROM $TABLE_MESSAGES m 
            INNER JOIN $TABLE_CHATS c ON m.$COLUMN_MESSAGE_CHAT_ID = c.$COLUMN_CHAT_ID 
            WHERE m.$COLUMN_MESSAGE_CONTENT LIKE ? 
            ORDER BY c.$COLUMN_CHAT_UPDATED_AT DESC, m.$COLUMN_MESSAGE_TIMESTAMP ASC
            """,
            arrayOf("%$query%")
        )

        cursor.use {
            while (it.moveToNext()) {
                // 创建消息实体
                val message = MessageEntity(
                    id = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_ID)),
                    chatId = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_CHAT_ID)),
                    content = it.getString(it.getColumnIndexOrThrow(COLUMN_MESSAGE_CONTENT)),
                    type = it.getInt(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TYPE)),
                    timestamp = Date(it.getLong(it.getColumnIndexOrThrow(COLUMN_MESSAGE_TIMESTAMP))),
                    isError = it.getInt(it.getColumnIndexOrThrow(COLUMN_MESSAGE_IS_ERROR)) == 1,
                    // 安全获取图片数据和内容类型
                    imageData = it.getStringOrNull(it.getColumnIndex(COLUMN_MESSAGE_IMAGE_DATA)),
                    contentType = it.getIntOrDefault(it.getColumnIndex(COLUMN_MESSAGE_CONTENT_TYPE), 0),
                    // 获取文档属性
                    documentSize = it.getStringOrNull(it.getColumnIndex(COLUMN_MESSAGE_DOCUMENT_SIZE)),
                    documentType = it.getStringOrNull(it.getColumnIndex(COLUMN_MESSAGE_DOCUMENT_TYPE))
                )

                // 创建聊天实体
                val chat = ChatEntity(
                    id = message.chatId,
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    createdAt = Date(it.getLong(it.getColumnIndexOrThrow("chat_created_at"))),
                    updatedAt = Date(it.getLong(it.getColumnIndexOrThrow("chat_updated_at"))),
                    aiPersona = it.getString(it.getColumnIndexOrThrow("ai_persona")),
                    modelType = it.getString(it.getColumnIndexOrThrow("model_type")),
                    isArchived = it.getInt(it.getColumnIndexOrThrow("is_archived")) == 1
                )

                results.add(MessageWithChat(message, chat))
            }
        }
        results
    }

    // --- 辅助方法 ---

    private fun Cursor.toChatEntity(): ChatEntity {
        return ChatEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_CHAT_ID)),
            title = getString(getColumnIndexOrThrow(COLUMN_CHAT_TITLE)),
            createdAt = Date(getLong(getColumnIndexOrThrow(COLUMN_CHAT_CREATED_AT))),
            updatedAt = Date(getLong(getColumnIndexOrThrow(COLUMN_CHAT_UPDATED_AT))),
            aiPersona = getString(getColumnIndexOrThrow(COLUMN_CHAT_AI_PERSONA)),
            modelType = getString(getColumnIndexOrThrow(COLUMN_CHAT_MODEL_TYPE)),
            isArchived = getInt(getColumnIndexOrThrow(COLUMN_CHAT_IS_ARCHIVED)) == 1
        )
    }

    private fun Cursor.toMessageEntity(): MessageEntity {
        // 尝试获取基本列
        val id = getString(getColumnIndexOrThrow(COLUMN_MESSAGE_ID))
        val chatId = getString(getColumnIndexOrThrow(COLUMN_MESSAGE_CHAT_ID))
        val content = getString(getColumnIndexOrThrow(COLUMN_MESSAGE_CONTENT))
        val type = getInt(getColumnIndexOrThrow(COLUMN_MESSAGE_TYPE))
        val timestamp = Date(getLong(getColumnIndexOrThrow(COLUMN_MESSAGE_TIMESTAMP)))
        val isError = getInt(getColumnIndexOrThrow(COLUMN_MESSAGE_IS_ERROR)) == 1

        // 安全获取图片数据
        val imageData = getStringOrNull(getColumnIndex(COLUMN_MESSAGE_IMAGE_DATA))

        // 安全获取内容类型
        val contentType = getIntOrDefault(getColumnIndex(COLUMN_MESSAGE_CONTENT_TYPE), 0)

        // 安全获取文档属性
        val documentSize = getStringOrNull(getColumnIndex(COLUMN_MESSAGE_DOCUMENT_SIZE))
        val documentType = getStringOrNull(getColumnIndex(COLUMN_MESSAGE_DOCUMENT_TYPE))

        // 添加调试日志
        Log.d(TAG, "加载消息: id=$id, 类型=$contentType, 图片数据=${
            if (imageData != null) "存在(${imageData.length/1024}KB)" else "无"
        }, 文档大小=$documentSize, 文档类型=$documentType")

        return MessageEntity(
            id = id,
            chatId = chatId,
            content = content,
            type = type,
            timestamp = timestamp,
            isError = isError,
            imageData = imageData,
            contentType = contentType,
            documentSize = documentSize,
            documentType = documentType
        )
    }

    // Cursor辅助方法
    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex == -1 || isNull(columnIndex)) null else getString(columnIndex)
    }

    private fun Cursor.getIntOrDefault(columnIndex: Int, defaultValue: Int): Int {
        return if (columnIndex == -1 || isNull(columnIndex)) defaultValue else getInt(columnIndex)
    }

    // 这是一个安全的记忆实体转换方法，可以处理可能缺失的列
    private fun Cursor.toMemoryEntitySafely(): MemoryEntity {
        // 防御性获取字段，处理可能缺失的列
        val keywords = try {
            val keywordsStr = getString(getColumnIndexOrThrow(COLUMN_MEMORY_KEYWORDS))
            if (keywordsStr.isNotEmpty()) keywordsStr.split(",") else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "keywords列不存在，使用空列表")
            emptyList<String>()
        }

        val category = try {
            getString(getColumnIndexOrThrow(COLUMN_MEMORY_CATEGORY))
        } catch (e: Exception) {
            Log.w(TAG, "category列不存在，使用默认值")
            "其他"
        }

        val importance = try {
            getInt(getColumnIndexOrThrow(COLUMN_MEMORY_IMPORTANCE))
        } catch (e: Exception) {
            Log.w(TAG, "importance列不存在，使用默认值")
            5
        }

        return MemoryEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_MEMORY_ID)),
            chatId = getString(getColumnIndexOrThrow(COLUMN_MEMORY_CHAT_ID)),
            content = getString(getColumnIndexOrThrow(COLUMN_MEMORY_CONTENT)),
            timestamp = Date(getLong(getColumnIndexOrThrow(COLUMN_MEMORY_TIMESTAMP))),
            startMessageId = getString(getColumnIndexOrThrow(COLUMN_MEMORY_START_MESSAGE_ID)),
            endMessageId = getString(getColumnIndexOrThrow(COLUMN_MEMORY_END_MESSAGE_ID)),
            category = category,
            importance = importance,
            keywords = keywords
        )
    }

    // 闹钟实体转换方法
    private fun Cursor.toAlarmEntity(): AlarmEntity {
        return AlarmEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_ALARM_ID)),
            triggerTime = getLong(getColumnIndexOrThrow(COLUMN_ALARM_TRIGGER_TIME)),
            title = getString(getColumnIndexOrThrow(COLUMN_ALARM_TITLE)),
            description = getString(getColumnIndexOrThrow(COLUMN_ALARM_DESCRIPTION)),
            isOneTime = getInt(getColumnIndexOrThrow(COLUMN_ALARM_IS_ONE_TIME)) == 1,
            repeatDays = getString(getColumnIndexOrThrow(COLUMN_ALARM_REPEAT_DAYS)),
            isActive = getInt(getColumnIndexOrThrow(COLUMN_ALARM_IS_ACTIVE)) == 1,
            createdAt = getLong(getColumnIndexOrThrow(COLUMN_ALARM_CREATED_AT))
        )
    }

    // 动态实体转换方法
    private fun Cursor.toMomentEntity(): MomentEntity {
        return MomentEntity(
            id = getString(getColumnIndexOrThrow(COLUMN_MOMENT_ID)),
            content = getString(getColumnIndexOrThrow(COLUMN_MOMENT_CONTENT)),
            type = getInt(getColumnIndexOrThrow(COLUMN_MOMENT_TYPE)),
            timestamp = Date(getLong(getColumnIndexOrThrow(COLUMN_MOMENT_TIMESTAMP))),
            imageUri = getString(getColumnIndexOrThrow(COLUMN_MOMENT_IMAGE_URI)),
            chatId = getString(getColumnIndexOrThrow(COLUMN_MOMENT_CHAT_ID)),
            title = getString(getColumnIndexOrThrow(COLUMN_MOMENT_TITLE)),
            isDeleted = getInt(getColumnIndexOrThrow(COLUMN_MOMENT_IS_DELETED)) == 1
        )
    }

    // --- 数据类 ---

    data class MessageWithChat(
        val message: MessageEntity,
        val chat: ChatEntity
    )

    // --- 用户反馈实体类 ---

    data class FeedbackEntity(
        val id: String,
        val chatId: String,
        val messageId: String,
        val userMessageId: String,
        val feedbackType: String,
        val confidence: Float,
        val content: String,
        val aspects: String,
        val keywords: String,
        val timestamp: Date,
        val processed: Boolean
    )
}
