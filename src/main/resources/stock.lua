-- KEYS[1]: 商品库存的 Redis Key (例如 goods:stock:1)
-- ARGV[1]: 想要扣减的数量 (通常是 1)
local stockKey = KEYS[1]
local count = tonumber(ARGV[1])

-- 1. 获取当前库存
local currentStock = redis.call('get', stockKey)

-- 2. 如果库存不存在，或者库存不够扣减，直接返回 -1 表示失败
if (not currentStock or tonumber(currentStock) < count) then
    return -1
end

-- 3. 库存足够，进行扣减并返回扣减后的新库存
return redis.call('decrby', stockKey, count)