/**
 * Script thực nghiệm tự động đánh giá API & Đồng bộ trên Backend SQLite thật (Port 3000)
 * Phục vụ minh chứng cho D4, D6, D7 (TC01 -> TC09)
 */
const http = require("http");

function request(method, path, body = null) {
    return new Promise((resolve, reject) => {
        const payload = body ? JSON.stringify(body) : null;
        const req = http.request({
            hostname: "127.0.0.1",
            port: 3000,
            path,
            method,
            headers: {
                "Content-Type": "application/json",
                ...(payload ? { "Content-Length": Buffer.byteLength(payload) } : {})
            }
        }, (res) => {
            let data = "";
            res.on("data", chunk => data += chunk);
            res.on("end", () => {
                try {
                    resolve({ status: res.statusCode, data: data ? JSON.parse(data) : null });
                } catch (e) {
                    resolve({ status: res.statusCode, data });
                }
            });
        });
        req.on("error", reject);
        if (payload) req.write(payload);
        req.end();
    });
}

async function runBenchmark() {
    console.log("==========================================================");
    console.log("BẮT ĐẦU KIỂM THỬ & ĐO LƯỜNG HIỆU NĂNG BACKEND REST API");
    console.log("==========================================================");

    const health = await request("GET", "/health");
    console.log("[1] Health Check (/health):", health.status, JSON.stringify(health.data));

    // Đo thời gian đồng bộ theo lô (10, 50, 100 bản ghi)
    const batchSizes = [10, 50, 100];
    for (const size of batchSizes) {
        const start = performance.now();
        let successCount = 0;
        const baseTime = Date.now();
        for (let i = 1; i <= size; i++) {
            const res = await request("POST", "/tasks", {
                id: `bench-${size}-${i}`,
                title: `Task thực nghiệm hiệu năng (${size} bản ghi) #${i}`,
                completed: i % 2 === 0,
                updatedAt: baseTime + i,
                isDeleted: false
            });
            if (res.status === 200 || res.status === 201) successCount++;
        }
        const getRes = await request("GET", "/tasks");
        const elapsedMs = (performance.now() - start).toFixed(2);
        console.log(`[Batch ${size} tasks] Thời gian Push + Pull: ${elapsedMs} ms | Thành công: ${successCount}/${size} (${((successCount/size)*100).toFixed(1)}%)`);
    }

    // Kiểm tra Idempotency (Gửi trùng lặp 5 lần cùng 1 bản ghi)
    const idempId = "test-idempotent-001";
    const idempTime = Date.now() + 50000;
    for (let k = 0; k < 5; k++) {
        await request("POST", "/tasks", {
            id: idempId,
            title: "Bản ghi kiểm tra gửi trùng 5 lần",
            completed: false,
            updatedAt: idempTime,
            isDeleted: false
        });
    }
    const allAfterIdemp = await request("GET", "/tasks");
    const countIdemp = allAfterIdemp.data.filter(t => t.id === idempId).length;
    console.log(`[Idempotency Check] Số bản ghi có id='${idempId}' sau 5 lần POST trùng: ${countIdemp} (Kỳ vọng: 1)`);

    // Kiểm tra Xung đột Last-Write-Wins (LWW) giữa 2 thiết bị
    const conflictId = "test-conflict-lww-001";
    const t0 = Date.now() + 60000;
    // Thiết bị B gửi bản mới hơn (t0 + 2000)
    await request("POST", "/tasks", {
        id: conflictId,
        title: "Thiết bị B sửa lúc t0 + 2000ms (MỚI HƠN)",
        completed: true,
        updatedAt: t0 + 2000,
        isDeleted: false
    });
    // Thiết bị A gửi bản cũ hơn (t0 + 1000) đến sau
    const conflictRes = await request("POST", "/tasks", {
        id: conflictId,
        title: "Thiết bị A sửa lúc t0 + 1000ms (CŨ HƠN)",
        completed: false,
        updatedAt: t0 + 1000,
        isDeleted: false
    });
    console.log(`[Conflict LWW Check] Kết quả sau khi Thiết bị A gửi bản cũ đè lên bản mới:`, JSON.stringify(conflictRes.data));
}

runBenchmark().catch(console.error);
