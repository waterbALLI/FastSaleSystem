-- 固定窗口计数器限流
-- KEYS[1]: rate:limit:{userId}:{goodsId}
-- ARGV[1]: 窗口内最大请求数
-- ARGV[2]: 窗口大小（秒）
local key = KEYS[1]
local maxCount = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = redis.call('get', key)
if current and tonumber(current) >= maxCount then
    return 0  -- 被限流
end

local count = redis.call('incr', key)
-- 只在第一次设置过期时间
if count == 1 then
    redis.call('expire', key, window)
end
return 1  -- 放行
