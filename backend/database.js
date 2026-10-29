
const Database = require("better-sqlite3");
const path = require("path");

// Lưu database trong thư mục backend
const db = new Database(
    path.join(__dirname, "tasks.db")
);

// Bật chế độ WAL
db.pragma("journal_mode = WAL");

// Tạo bảng tasks nếu chưa tồn tại
db.exec(`
    CREATE TABLE IF NOT EXISTS tasks (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        completed INTEGER NOT NULL DEFAULT 0,
        updatedAt INTEGER NOT NULL,
        isDeleted INTEGER NOT NULL DEFAULT 0
    )
`);

module.exports = db;
