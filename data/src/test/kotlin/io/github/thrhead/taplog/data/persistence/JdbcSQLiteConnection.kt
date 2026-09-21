package io.github.thrhead.taplog.data.persistence

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types

/** Host SQLite for exercising Room's actual schema reader without Android cursor mocks. */
internal class JdbcSQLiteConnection : SQLiteConnection {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    fun execute(sql: String) = connection.createStatement().use { it.execute(sql) }

    override fun prepare(sql: String): SQLiteStatement = JdbcStatement(connection, sql)
    override fun close() = connection.close()

    private class JdbcStatement(connection: Connection, sql: String) : SQLiteStatement {
        private val statement = connection.prepareStatement(sql)
        private var rows: ResultSet? = null
        private var executed = false

        override fun bindBlob(index: Int, value: ByteArray) = statement.setBytes(index, value)
        override fun bindDouble(index: Int, value: Double) = statement.setDouble(index, value)
        override fun bindLong(index: Int, value: Long) = statement.setLong(index, value)
        override fun bindText(index: Int, value: String) = statement.setString(index, value)
        override fun bindNull(index: Int) = statement.setNull(index, Types.NULL)
        override fun getBlob(index: Int): ByteArray = rows!!.getBytes(index + 1)
        override fun getDouble(index: Int): Double = rows!!.getDouble(index + 1)
        override fun getLong(index: Int): Long = rows!!.getLong(index + 1)
        override fun getText(index: Int): String = rows!!.getString(index + 1)
        override fun isNull(index: Int): Boolean = rows!!.getObject(index + 1) == null
        override fun getColumnCount(): Int {
            executeOnce()
            return rows!!.metaData.columnCount
        }
        override fun getColumnName(index: Int): String {
            executeOnce()
            return rows!!.metaData.getColumnName(index + 1)
        }
        override fun getColumnType(index: Int): Int = when (rows!!.getObject(index + 1)) {
            null -> 5
            is ByteArray -> 4
            is Float, is Double -> 2
            is Number -> 1
            else -> 3
        }

        override fun step(): Boolean {
            executeOnce()
            return rows?.next() ?: false
        }

        private fun executeOnce() {
            if (!executed) {
                executed = true
                if (statement.execute()) rows = statement.resultSet
            }
        }

        override fun reset() {
            rows?.close()
            rows = null
            executed = false
        }

        override fun clearBindings() = statement.clearParameters()
        override fun close() = statement.close()
    }
}
