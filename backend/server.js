
const express = require("express");
const cors = require("cors");
const { randomUUID } = require("crypto");
const db = require("./database");

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

function toDto(task) {
    return {
        id: task.id,
        title: task.title,
        completed: Boolean(task.completed),
        updatedAt: task.updatedAt,
        isDeleted: Boolean(task.isDeleted)
    };
}

function findTask(id) {
    return db.prepare("SELECT * FROM tasks WHERE id = ?").get(id);
}

function saveTask(task) {
    db.prepare(`
        INSERT INTO tasks (id, title, completed, updatedAt, isDeleted)
        VALUES (@id, @title, @completed, @updatedAt, @isDeleted)
        ON CONFLICT(id) DO UPDATE SET
            title = excluded.title,
            completed = excluded.completed,
            updatedAt = excluded.updatedAt,
            isDeleted = excluded.isDeleted
    `).run({
        id: task.id,
        title: task.title,
        completed: Number(task.completed),
        updatedAt: task.updatedAt,
        isDeleted: Number(task.isDeleted)
    });
}

app.get("/health", (req, res) => {
    res.json({ status: "OK", message: "API is running" });
});

app.get("/tasks", (req, res) => {
    try {
        const since = Number(req.query.since);
        if (Number.isFinite(since) && since > 0) {
            const tasks = db.prepare(
                "SELECT * FROM tasks WHERE updatedAt > ? ORDER BY updatedAt DESC"
            ).all(since);
            return res.json(tasks.map(toDto));
        }

        const tasks = db.prepare(
            "SELECT * FROM tasks ORDER BY updatedAt DESC"
        ).all();
        res.json(tasks.map(toDto));
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Database error" });
    }
});

// Tạo hoặc gửi lại cùng một công việc
app.post("/tasks", (req, res) => {
    try {
        const {
            id = randomUUID(),
            title,
            completed = false,
            updatedAt = Date.now(),
            isDeleted = false
        } = req.body || {};

        if (
            typeof id !== "string" || !id ||
            typeof title !== "string" || !title.trim() ||
            typeof completed !== "boolean" ||
            typeof isDeleted !== "boolean" ||
            !Number.isSafeInteger(updatedAt) || updatedAt < 0
        ) {
            return res.status(400).json({ error: "Invalid task" });
        }

        const existing = findTask(id);

        // Phiên bản cũ hoặc gửi lại: trả về bản đang lưu
        if (existing && existing.updatedAt >= updatedAt) {
            return res.status(200).json(toDto(existing));
        }

        const task = {
            id,
            title: title.trim(),
            completed,
            updatedAt,
            isDeleted
        };

        saveTask(task);
        res.status(existing ? 200 : 201).json(task);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Database error" });
    }
});

// Cập nhật công việc theo phiên bản
app.put("/tasks/:id", (req, res) => {
    try {
        const existing = findTask(req.params.id);

        if (!existing) {
            return res.status(404).json({ error: "Task not found" });
        }

        const body = req.body || {};
        const title = body.title ?? existing.title;
        const completed = body.completed ?? Boolean(existing.completed);
        const isDeleted = body.isDeleted ?? Boolean(existing.isDeleted);
        const updatedAt = body.updatedAt ?? Date.now();

        if (
            typeof title !== "string" || !title.trim() ||
            typeof completed !== "boolean" ||
            typeof isDeleted !== "boolean" ||
            !Number.isSafeInteger(updatedAt) || updatedAt < 0
        ) {
            return res.status(400).json({ error: "Invalid task" });
        }

        if (updatedAt <= existing.updatedAt) {
            return res.status(200).json(toDto(existing));
        }

        const task = {
            id: req.params.id,
            title: title.trim(),
            completed,
            updatedAt,
            isDeleted
        };

        saveTask(task);
        res.json(task);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Database error" });
    }
});

// Xóa mềm
app.delete("/tasks/:id", (req, res) => {
    try {
        const existing = findTask(req.params.id);

        if (!existing) {
            return res.status(404).json({ error: "Task not found" });
        }

        const updatedAt = Math.max(
            Date.now(),
            existing.updatedAt + 1
        );

        db.prepare(`
            UPDATE tasks
            SET isDeleted = 1, updatedAt = ?
            WHERE id = ?
        `).run(updatedAt, req.params.id);

        res.json({
            id: req.params.id,
            message: "Task soft deleted",
            isDeleted: true,
            updatedAt
        });
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Database error" });
    }
});

app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running at http://localhost:${PORT}`);
});
