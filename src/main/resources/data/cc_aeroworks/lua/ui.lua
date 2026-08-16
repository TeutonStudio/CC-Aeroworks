local native = require("cc_aeroworks.ui_native")
local rawTelemetry = require("cc_aeroworks.telemetry")

local ui = {}
local states = {}
local remembered = {}
local derived = {}
local derivedByScope = {}
local moduleCache = {}
local controllerCache = {}
local activeRuntime = nil
local composer = nil

local function withScope(id, phase, fn)
    native.beginScope(id, phase)
    local packed = table.pack(pcall(fn))
    native.endScope()
    if not packed[1] then error(packed[2], 0) end
    return table.unpack(packed, 2, packed.n)
end

local function copyTable(value)
    local result = {}
    if value then for k, v in pairs(value) do result[k] = v end end
    return result
end

local function runtimeKey(key)
    assert(activeRuntime and composer and composer.node,
        "ui.state/ui.remember/ui.derived must be created while composing a UI component")
    return composer.node.id .. ":remember:" .. tostring(key)
end

local function propsEqual(a, b)
    if a == b then return true end
    if type(a) ~= "table" or type(b) ~= "table" then return false end
    for k, v in pairs(a) do
        local other = b[k]
        if type(v) == "function" and type(other) == "function" then
            -- Closures are commonly recreated by a parent recomposition. Treat callbacks as
            -- stable; state read by them is tracked when the callback actually runs.
        elseif v ~= other then
            return false
        end
    end
    for k, v in pairs(b) do
        if a[k] == nil and v ~= nil then return false end
    end
    return true
end

function ui.remember(key, factory)
    local id = runtimeKey(key)
    local slot = remembered[id]
    if not slot then
        slot = { value = type(factory) == "function" and factory() or factory }
        remembered[id] = slot
    end
    return slot.value
end

function ui.state(key, initial)
    local id = runtimeKey(key)
    local state = states[id]
    if not state then
        state = { value = type(initial) == "function" and initial() or initial }
        states[id] = state
    end
    local dependency = "state:" .. id
    return {
        get = function()
            native.read(dependency)
            return state.value
        end,
        set = function(value)
            if state.value == value then return false end
            state.value = value
            native.changed(dependency)
            return true
        end,
        update = function(fn)
            local nextValue = fn(state.value)
            if state.value == nextValue then return state.value end
            state.value = nextValue
            native.changed(dependency)
            return state.value
        end
    }
end

function ui.derived(key, calculation, equals)
    local id = runtimeKey(key)
    local existing = derived[id]
    if existing then return existing.public end
    local dependency = "derived:" .. id
    local scopeId = "derived:" .. id
    local entry = { initialized = false, value = nil }

    function entry.recompute()
        local nextValue = withScope(scopeId, "composition", calculation)
        local same = entry.initialized and ((equals and equals(entry.value, nextValue)) or (not equals and entry.value == nextValue))
        entry.value = nextValue
        local first = not entry.initialized
        entry.initialized = true
        if not first and not same then native.changed(dependency) end
        return not same
    end

    entry.public = {
        get = function()
            native.read(dependency)
            if not entry.initialized then entry.recompute() end
            return entry.value
        end,
        invalidate = function()
            entry.recompute()
        end
    }
    derived[id] = entry
    derivedByScope[scopeId] = entry
    return entry.public
end

ui.telemetry = setmetatable({}, { __index = rawTelemetry })

function ui.telemetry.get(nameOrId)
    local value = rawTelemetry.get(nameOrId)
    if value and value.id then
        native.read("telemetry:" .. tostring(value.id))
    else
        native.read("telemetry:*")
    end
    return value
end

function ui.telemetry.list()
    native.read("telemetry:*")
    return rawTelemetry.list()
end

function ui.telemetry.find(kind)
    native.read("telemetry:*")
    return rawTelemetry.find(kind)
end

function ui.source(key, getter)
    local dependency = tostring(key)
    return {
        get = function()
            native.read(dependency)
            return getter()
        end
    }
end

function ui.invalidate(key)
    native.changed(tostring(key))
end

ui.input = {}

function ui.input.pointer()
    assert(activeRuntime, "ui.input.pointer must be created while composing a UI component")
    local runtime = activeRuntime
    local dependency = runtime.id .. ":input:pointer"
    return {
        get = function()
            native.read(dependency)
            if not runtime.pointer then return nil end
            return copyTable(runtime.pointer)
        end
    }
end

local modifierMethods = {}
modifierMethods.__index = modifierMethods

local function modifierSet(self, key, value)
    local nextValue = setmetatable({ values = copyTable(self.values) }, modifierMethods)
    nextValue.values[key] = value
    return nextValue
end

function modifierMethods:width(value) return modifierSet(self, "width", value) end
function modifierMethods:height(value) return modifierSet(self, "height", value) end
function modifierMethods:fillWidth() return modifierSet(self, "fillWidth", true) end
function modifierMethods:fillHeight() return modifierSet(self, "fillHeight", true) end
function modifierMethods:fillMaxSize() return modifierSet(modifierSet(self, "fillWidth", true), "fillHeight", true) end
function modifierMethods:padding(value) return modifierSet(self, "padding", value) end
function modifierMethods:key(value) return modifierSet(self, "key", value) end

function ui.modifier()
    return setmetatable({ values = {} }, modifierMethods)
end

local function effectiveProps(props)
    props = props or {}
    local result = copyTable(props)
    if props.modifier and props.modifier.values then
        for k, v in pairs(props.modifier.values) do
            if result[k] == nil then result[k] = v end
        end
    end
    return result
end

local function disposeRemembered(node)
    local prefix = node.id .. ":remember:"
    for id in pairs(states) do
        if string.sub(id, 1, #prefix) == prefix then states[id] = nil end
    end
    for id in pairs(remembered) do
        if string.sub(id, 1, #prefix) == prefix then remembered[id] = nil end
    end
    local removeDerived = {}
    for id in pairs(derived) do
        if string.sub(id, 1, #prefix) == prefix then table.insert(removeDerived, id) end
    end
    for _, id in ipairs(removeDerived) do
        local scopeId = "derived:" .. id
        native.forgetScope(scopeId)
        derivedByScope[scopeId] = nil
        derived[id] = nil
    end
end

local function disposeNode(runtime, node)
    if not node then return end
    if node.children then
        for _, child in ipairs(node.children) do disposeNode(runtime, child) end
    end
    disposeRemembered(node)
    native.forgetScope(node.id .. ":composition")
    native.forgetScope(node.id .. ":layout")
    native.forgetScope(node.id .. ":draw")
    runtime.nodes[node.id] = nil
    runtime.components[node.id] = nil
end

local function nextNodeId(kind, props)
    assert(composer, "UI nodes may only be emitted while composing an app")
    composer.index = composer.index + 1
    local key = props and props.key
    local suffix = key ~= nil and tostring(key) or tostring(composer.index)
    return composer.node.id .. "/" .. kind .. ":" .. suffix
end

local function acquireNode(kind, props)
    local runtime = composer.runtime
    local id = nextNodeId(kind, props)
    local node = runtime.nodes[id]
    if not node then
        node = { id = id, kind = kind, children = {}, props = {}, bounds = nil }
        runtime.nodes[id] = node
    end
    node.kind = kind
    node.props = effectiveProps(props)
    return node
end

local function emit(node)
    table.insert(composer.node.children, node)
end

local function withChildren(node, content)
    local oldComposer = composer
    local oldChildren = node.children or {}
    node.children = {}
    composer = { runtime = oldComposer.runtime, node = node, index = 0 }
    if content then content() end
    composer = oldComposer

    local retained = {}
    for _, child in ipairs(node.children) do retained[child.id] = true end
    for _, child in ipairs(oldChildren) do
        if not retained[child.id] then disposeNode(oldComposer.runtime, child) end
    end
end

local function container(kind, props, content)
    local node = acquireNode(kind, props)
    emit(node)
    withChildren(node, content)
    return node
end

function ui.Column(props, content) return container("column", props, content) end
function ui.Row(props, content) return container("row", props, content) end
function ui.Box(props, content) return container("box", props, content) end

function ui.Spacer(props)
    local node = acquireNode("spacer", props)
    node.children = {}
    emit(node)
    return node
end

function ui.Text(value)
    local props = type(value) == "table" and value or { text = value }
    local node = acquireNode("text", props)
    node.children = {}
    emit(node)
    return node
end

function ui.ProgressBar(props)
    local node = acquireNode("progress", props or {})
    node.children = {}
    emit(node)
    return node
end

function ui.Button(props)
    local node = acquireNode("button", props or {})
    node.children = {}
    emit(node)
    return node
end

function ui.Canvas(props)
    local node = acquireNode("canvas", props or {})
    node.children = {}
    emit(node)
    return node
end

local function composeComponent(runtime, node)
    node.invalidComposition = false
    withScope(node.id .. ":composition", "composition", function()
        local previousRuntime = activeRuntime
        local previousComposer = composer
        activeRuntime = runtime
        local oldChildren = node.children or {}
        node.children = {}
        composer = { runtime = runtime, node = node, index = 0 }
        node.componentFn(node.props or {})
        composer = previousComposer
        activeRuntime = previousRuntime

        local retained = {}
        for _, child in ipairs(node.children) do retained[child.id] = true end
        for _, child in ipairs(oldChildren) do
            if not retained[child.id] then disposeNode(runtime, child) end
        end
    end)
end

function ui.component(name, fn)
    assert(type(name) == "string" and name ~= "", "component requires a name")
    assert(type(fn) == "function", "component requires a function")
    return function(props)
        props = props or {}
        local node = acquireNode("component_" .. name, props)
        local previousProps = node.componentProps
        node.componentFn = fn
        node.componentProps = props
        emit(node)
        composer.runtime.components[node.id] = node
        if not node.composed or node.invalidComposition or not propsEqual(previousProps, props) then
            node.composed = true
            composeComponent(composer.runtime, node)
        end
        return node
    end
end

function ui.key(key, content)
    return ui.Box({ key = key }, content)
end

function ui.WithConstraints(content)
    assert(activeRuntime, "WithConstraints must run inside a mounted app")
    return content({ width = activeRuntime.width, height = activeRuntime.height })
end

function ui.navigator(key, initial)
    local state = ui.state("navigator:" .. tostring(key), { current = initial, stack = {} })
    return {
        get = function() return state.get().current end,
        go = function(route)
            state.update(function(old)
                local stack = {}
                for i, value in ipairs(old.stack) do stack[i] = value end
                table.insert(stack, old.current)
                return { current = route, stack = stack }
            end)
        end,
        replace = function(route)
            state.update(function(old) return { current = route, stack = old.stack } end)
        end,
        back = function()
            state.update(function(old)
                if #old.stack == 0 then return old end
                local stack = {}
                for i = 1, #old.stack - 1 do stack[i] = old.stack[i] end
                return { current = old.stack[#old.stack], stack = stack }
            end)
        end
    }
end

local function loadModule(path)
    if moduleCache[path] ~= nil then return moduleCache[path] end
    local chunk, loadError = loadfile(path)
    if not chunk then error("display app " .. path .. ": " .. tostring(loadError), 0) end
    local ok, value = pcall(chunk)
    if not ok then error("display app " .. path .. ": " .. tostring(value), 0) end
    moduleCache[path] = value
    return value
end

function ui.Route(navigator, routes)
    local route = navigator.get()
    local content = routes[route]
    if type(content) == "string" then content = loadModule(content) end
    if type(content) == "table" and content.__uiApp then content = content.root end
    if type(content) == "function" then return content() end
    return nil
end

function ui.LazyColumn(props, itemContent)
    props = props or {}
    local items = props.items or {}
    local itemHeight = math.max(1, props.itemHeight or 6)
    local viewportHeight = props.viewportHeight or (activeRuntime and activeRuntime.height or itemHeight)
    local scroll = props.scroll
    local offset = scroll and scroll.get() or (props.offset or 0)
    local first = math.max(1, math.floor(offset / itemHeight) + 1)
    local count = math.ceil(viewportHeight / itemHeight) + 1
    local last = math.min(#items, first + count - 1)
    return ui.Column(props, function()
        for index = first, last do
            local item = items[index]
            local key = props.key and props.key(item, index) or index
            ui.key(key, function() itemContent(item, index) end)
        end
    end)
end

function ui.app(root, options)
    assert(type(root) == "function", "ui.app requires a root function")
    return { __uiApp = true, root = root, options = options or {} }
end

local function valueOf(value, default)
    if value == nil then return default end
    if type(value) == "function" then return value() end
    return value
end

local function textOf(props)
    return tostring(valueOf(props.text, ""))
end

local function clamp(value, low, high)
    return math.max(low, math.min(high, value))
end

local function layoutNode(runtime, node, x, y, maxWidth, maxHeight)
    return withScope(node.id .. ":layout", "layout", function()
        local props = node.props or {}
        local padding = math.max(0, math.floor(valueOf(props.padding, 0)))
        local gap = math.max(0, math.floor(valueOf(props.gap, 0)))
        local requestedWidth = valueOf(props.width, nil)
        local requestedHeight = valueOf(props.height, nil)
        local width, height

        if node.kind == "text" then
            width = requestedWidth or math.max(0, #textOf(props) * 4 - 1)
            height = requestedHeight or 5
        elseif node.kind == "progress" then
            width = requestedWidth or maxWidth
            height = requestedHeight or 3
        elseif node.kind == "button" then
            width = requestedWidth or math.max(5, #textOf(props) * 4 + 3)
            height = requestedHeight or 7
        elseif node.kind == "spacer" or node.kind == "canvas" then
            width = requestedWidth or (props.fillWidth and maxWidth or 1)
            height = requestedHeight or (props.fillHeight and maxHeight or 1)
        elseif node.kind == "row" then
            local cursor = x + padding
            local childHeight = 0
            local innerMaxWidth = math.max(0, (requestedWidth or maxWidth) - padding * 2)
            local innerMaxHeight = math.max(0, (requestedHeight or maxHeight) - padding * 2)
            for index, child in ipairs(node.children) do
                local cw, ch = layoutNode(runtime, child, cursor, y + padding, innerMaxWidth, innerMaxHeight)
                cursor = cursor + cw + (index < #node.children and gap or 0)
                childHeight = math.max(childHeight, ch)
            end
            width = requestedWidth or (cursor - x + padding)
            height = requestedHeight or (childHeight + padding * 2)
        elseif node.kind == "column" or string.sub(node.kind, 1, 10) == "component_" then
            local cursor = y + padding
            local childWidth = 0
            local innerMaxWidth = math.max(0, (requestedWidth or maxWidth) - padding * 2)
            local innerMaxHeight = math.max(0, (requestedHeight or maxHeight) - padding * 2)
            for index, child in ipairs(node.children) do
                local cw, ch = layoutNode(runtime, child, x + padding, cursor, innerMaxWidth, innerMaxHeight)
                cursor = cursor + ch + (index < #node.children and gap or 0)
                childWidth = math.max(childWidth, cw)
            end
            width = requestedWidth or (childWidth + padding * 2)
            height = requestedHeight or (cursor - y + padding)
        else -- box
            local childWidth, childHeight = 0, 0
            local innerMaxWidth = math.max(0, (requestedWidth or maxWidth) - padding * 2)
            local innerMaxHeight = math.max(0, (requestedHeight or maxHeight) - padding * 2)
            for _, child in ipairs(node.children) do
                local cw, ch = layoutNode(runtime, child, x + padding, y + padding, innerMaxWidth, innerMaxHeight)
                childWidth = math.max(childWidth, cw)
                childHeight = math.max(childHeight, ch)
            end
            width = requestedWidth or (childWidth + padding * 2)
            height = requestedHeight or (childHeight + padding * 2)
        end

        if props.fillWidth then width = maxWidth end
        if props.fillHeight then height = maxHeight end
        width = clamp(math.floor(width or 0), 0, maxWidth)
        height = clamp(math.floor(height or 0), 0, maxHeight)
        node.bounds = { x = x, y = y, width = width, height = height }
        return width, height
    end)
end

local FONT = {
    ["0"]={"111","101","101","101","111"}, ["1"]={"010","110","010","010","111"},
    ["2"]={"111","001","111","100","111"}, ["3"]={"111","001","111","001","111"},
    ["4"]={"101","101","111","001","001"}, ["5"]={"111","100","111","001","111"},
    ["6"]={"111","100","111","101","111"}, ["7"]={"111","001","010","010","010"},
    ["8"]={"111","101","111","101","111"}, ["9"]={"111","101","111","001","111"},
    A={"010","101","111","101","101"}, B={"110","101","110","101","110"},
    C={"011","100","100","100","011"}, D={"110","101","101","101","110"},
    E={"111","100","110","100","111"}, F={"111","100","110","100","100"},
    G={"011","100","101","101","011"}, H={"101","101","111","101","101"},
    I={"111","010","010","010","111"}, J={"001","001","001","101","010"},
    K={"101","101","110","101","101"}, L={"100","100","100","100","111"},
    M={"101","111","111","101","101"}, N={"101","111","111","111","101"},
    O={"010","101","101","101","010"}, P={"110","101","110","100","100"},
    Q={"010","101","101","111","011"}, R={"110","101","110","101","101"},
    S={"011","100","010","001","110"}, T={"111","010","010","010","010"},
    U={"101","101","101","101","111"}, V={"101","101","101","101","010"},
    W={"101","101","111","111","101"}, X={"101","101","010","101","101"},
    Y={"101","101","010","010","010"}, Z={"111","001","010","100","111"},
    ["-"]={"000","000","111","000","000"}, ["+"]={"000","010","111","010","000"},
    ["%"]={"101","001","010","100","101"}, [":"]={"000","010","000","010","000"},
    ["."]={"000","000","000","000","010"}, ["/"]={"001","001","010","100","100"},
    [" "]={"000","000","000","000","000"}
}

local function intersects(a, b)
    if not a or not b then return false end
    return a.x < b.x + b.width and b.x < a.x + a.width and a.y < b.y + b.height and b.y < a.y + a.height
end

local function unionRect(a, b)
    if not a then return b and { x=b.x,y=b.y,width=b.width,height=b.height } or nil end
    if not b then return a end
    local x1, y1 = math.min(a.x,b.x), math.min(a.y,b.y)
    local x2 = math.max(a.x+a.width,b.x+b.width)
    local y2 = math.max(a.y+a.height,b.y+b.height)
    return { x=x1, y=y1, width=x2-x1, height=y2-y1 }
end

local function rectEquals(a, b)
    if a == nil or b == nil then return a == b end
    return a.x == b.x and a.y == b.y and a.width == b.width and a.height == b.height
end

local function snapshotBounds(runtime)
    local result = {}
    for id, node in pairs(runtime.nodes) do
        if node.bounds then result[id] = copyTable(node.bounds) end
    end
    return result
end

local function safeFill(runtime, frame, x, y, width, height, enabled, clip)
    local x1 = math.max(1, x)
    local y1 = math.max(1, y)
    local x2 = math.min(runtime.width + 1, x + width)
    local y2 = math.min(runtime.height + 1, y + height)
    if clip then
        x1 = math.max(x1, clip.x); y1 = math.max(y1, clip.y)
        x2 = math.min(x2, clip.x + clip.width); y2 = math.min(y2, clip.y + clip.height)
    end
    if x2 > x1 and y2 > y1 then frame.fillRect(x1, y1, x2-x1, y2-y1, enabled) end
end

local function drawText(runtime, frame, text, x, y, scale, clip)
    text = string.upper(tostring(text))
    scale = math.max(1, math.floor(scale or 1))
    local cursor = x
    for index = 1, #text do
        local glyph = FONT[string.sub(text,index,index)] or FONT[" "]
        for gy = 1, 5 do
            local row = glyph[gy]
            local runStart = nil
            for gx = 1, 4 do
                local on = gx <= 3 and string.sub(row,gx,gx) == "1"
                if on and not runStart then runStart = gx end
                if runStart and (not on or gx == 4) then
                    local runEnd = on and gx or gx-1
                    safeFill(runtime, frame, cursor + (runStart-1)*scale, y + (gy-1)*scale,
                        (runEnd-runStart+1)*scale, scale, true, clip)
                    runStart = nil
                end
            end
        end
        cursor = cursor + 4*scale
    end
end

local function drawNode(runtime, frame, node, clip)
    if not node.bounds or not intersects(node.bounds, clip) then return end
    withScope(node.id .. ":draw", "draw", function()
        local p, b = node.props or {}, node.bounds
        if p.background then safeFill(runtime, frame, b.x,b.y,b.width,b.height,true,clip) end
        if p.border and b.width > 1 and b.height > 1 then
            safeFill(runtime,frame,b.x,b.y,b.width,1,true,clip)
            safeFill(runtime,frame,b.x,b.y+b.height-1,b.width,1,true,clip)
            safeFill(runtime,frame,b.x,b.y,1,b.height,true,clip)
            safeFill(runtime,frame,b.x+b.width-1,b.y,1,b.height,true,clip)
        end
        if node.kind == "text" then
            drawText(runtime, frame, textOf(p), b.x, b.y, valueOf(p.scale,1), clip)
        elseif node.kind == "progress" then
            local value = clamp(tonumber(valueOf(p.value,0)) or 0, 0, 1)
            if p.border ~= false and b.width >= 2 and b.height >= 2 then
                safeFill(runtime,frame,b.x,b.y,b.width,1,true,clip)
                safeFill(runtime,frame,b.x,b.y+b.height-1,b.width,1,true,clip)
                safeFill(runtime,frame,b.x,b.y,1,b.height,true,clip)
                safeFill(runtime,frame,b.x+b.width-1,b.y,1,b.height,true,clip)
                local inner = math.floor((b.width-2)*value + 0.5)
                safeFill(runtime,frame,b.x+1,b.y+1,inner,math.max(0,b.height-2),true,clip)
            else
                safeFill(runtime,frame,b.x,b.y,math.floor(b.width*value+0.5),b.height,true,clip)
            end
        elseif node.kind == "button" then
            safeFill(runtime,frame,b.x,b.y,b.width,1,true,clip)
            safeFill(runtime,frame,b.x,b.y+b.height-1,b.width,1,true,clip)
            safeFill(runtime,frame,b.x,b.y,1,b.height,true,clip)
            safeFill(runtime,frame,b.x+b.width-1,b.y,1,b.height,true,clip)
            drawText(runtime,frame,textOf(p),b.x+2,b.y+1,valueOf(p.scale,1),clip)
        elseif node.kind == "canvas" and type(p.draw) == "function" then
            local canvas = {
                width=b.width, height=b.height,
                setPixel=function(px,py,on) safeFill(runtime,frame,b.x+px-1,b.y+py-1,1,1,on~=false,clip) end,
                fillRect=function(px,py,w,h,on) safeFill(runtime,frame,b.x+px-1,b.y+py-1,w,h,on~=false,clip) end,
                text=function(text,px,py,scale) drawText(runtime,frame,text,b.x+px-1,b.y+py-1,scale,clip) end
            }
            p.draw(canvas)
        end
    end)
    for _, child in ipairs(node.children or {}) do drawNode(runtime, frame, child, clip) end
end

local Runtime = {}
Runtime.__index = Runtime

local function asApp(value)
    if type(value) == "table" and value.__uiApp then return value end
    if type(value) == "function" then return ui.app(value) end
    error("Display app must return ui.app(...) or a component function", 0)
end

function Runtime:composeRoot()
    local root = self.root
    root.componentFn = self.spec.root
    root.props = {}
    composeComponent(self, root)
end

function Runtime:layout()
    layoutNode(self, self.root, 1, 1, self.width, self.height)
    self.root.bounds = { x=1,y=1,width=self.width,height=self.height }
end

function Runtime:drawFull()
    local frame = native.beginFrame(self.deskId, self.socket)
    frame.clear()
    drawNode(self, frame, self.root, {x=1,y=1,width=self.width,height=self.height})
    frame.commit()
end

function Runtime:drawDirty(rect)
    if not rect or rect.width <= 0 or rect.height <= 0 then return end
    rect.x = clamp(rect.x,1,self.width)
    rect.y = clamp(rect.y,1,self.height)
    rect.width = clamp(rect.width,0,self.width-rect.x+1)
    rect.height = clamp(rect.height,0,self.height-rect.y+1)
    local frame = native.beginFrame(self.deskId, self.socket)
    frame.fillRect(rect.x, rect.y, rect.width, rect.height, false)
    drawNode(self, frame, self.root, rect)
    frame.commit()
end

function Runtime:switchApp(pathOrSpec)
    local spec = pathOrSpec
    local path = nil
    if type(pathOrSpec) == "string" then
        path = pathOrSpec
        spec = asApp(loadModule(pathOrSpec))
    else
        spec = asApp(spec)
    end
    if self.root then disposeNode(self, self.root) end
    self.nodes = {}
    self.components = {}
    self.spec = spec
    self.appPath = path
    self.root = { id=self.id.."/root",kind="component_root",children={},props={},bounds=nil,componentFn=spec.root }
    self.nodes[self.root.id] = self.root
    self.components[self.root.id] = self.root
    self:composeRoot()
    self:layout()
    self:drawFull()
end

function Runtime:dispose()
    if self.root then disposeNode(self, self.root) end
    self.root = nil
    self.nodes = {}
    self.components = {}
    self.pointer = nil
    native.clearFrame(self.deskId, self.socket)
end

function Runtime:applyInvalidations(invalidations)
    local relevant = {}
    local needsRelayout = false
    for _, inv in ipairs(invalidations) do
        if string.sub(inv.id,1,#self.id) == self.id then
            table.insert(relevant,inv)
            if inv.phase == "composition" or inv.phase == "layout" then needsRelayout = true end
        end
    end
    if #relevant == 0 then return end

    local before = needsRelayout and snapshotBounds(self) or nil
    local explicitlyDirtyIds = {}
    local dirty = nil

    for _, inv in ipairs(relevant) do
        local suffix = ":" .. inv.phase
        local nodeId = string.sub(inv.id,1,#inv.id-#suffix)
        local node = self.nodes[nodeId]
        if node then explicitlyDirtyIds[nodeId] = true end

        if inv.phase == "composition" and node and node.componentFn then
            node.invalidComposition = true
            composeComponent(self,node)
        elseif inv.phase == "draw" and node then
            dirty = unionRect(dirty,node.bounds)
        end
    end

    if needsRelayout then
        self:layout()
        local seen = {}
        for id, oldBounds in pairs(before) do
            seen[id] = true
            local node = self.nodes[id]
            local newBounds = node and node.bounds or nil
            if not rectEquals(oldBounds,newBounds) or explicitlyDirtyIds[id] then
                dirty = unionRect(dirty,oldBounds)
                dirty = unionRect(dirty,newBounds)
            end
        end
        for id, node in pairs(self.nodes) do
            if not seen[id] and node.bounds then
                dirty = unionRect(dirty,node.bounds)
            elseif explicitlyDirtyIds[id] and node.bounds then
                dirty = unionRect(dirty,node.bounds)
            end
        end
    end
    self:drawDirty(dirty)
end

local function hitNode(node, x, y)
    if not node.bounds then return nil end
    local b = node.bounds
    if x < b.x or y < b.y or x >= b.x+b.width or y >= b.y+b.height then return nil end
    for index = #(node.children or {}), 1, -1 do
        local hit = hitNode(node.children[index],x,y)
        if hit then return hit end
    end
    local p = node.props or {}
    if p.onTap or p.onDoubleTap or p.onPointer then return node end
    return nil
end

function Runtime:updatePointer(event)
    self.pointerRevision = (self.pointerRevision or 0) + 1
    local snapshot = copyTable(event)
    snapshot.revision = self.pointerRevision
    self.pointer = snapshot
    native.changed(self.id .. ":input:pointer")
end

function Runtime:handlePointer(event)
    if event.deskId ~= self.deskId or event.socket ~= self.socket then return false end
    self:updatePointer(event)

    if self.controller and self.controller ~= false then
        local callback = event.action == "tap" and self.controller.onTap
            or event.action == "double_tap" and self.controller.onDoubleTap
            or self.controller.onPointer
        if type(callback) == "function" then
            local ok, result = pcall(callback,event,self)
            if not ok then
                printError(tostring(result))
            elseif type(result) == "string" and result ~= "" then
                self:switchApp(result)
                return true
            end
        end
    end

    local node = hitNode(self.root,event.x,event.y)
    if node then
        local p = node.props or {}
        local callback = event.action == "tap" and p.onTap
            or event.action == "double_tap" and p.onDoubleTap
            or p.onPointer
        if type(callback) == "function" then
            local ok, err = pcall(callback,event)
            if not ok then printError(tostring(err)) end
        end
    end
    return true
end

function Runtime:getInfo()
    return {
        deskId=self.deskId,
        socket=self.socket,
        width=self.width,
        height=self.height,
        app=self.appPath,
        pointerRevision=self.pointerRevision
    }
end

local function loadController(path)
    if not path or path == "" then return nil end
    local controller = controllerCache[path]
    if controller ~= nil then return controller end
    local ok, value = pcall(loadModule,path)
    controller = ok and value or false
    controllerCache[path] = controller
    if not ok then printError(tostring(value)) end
    return controller
end

local function newRuntime(display, spec)
    local runtime = setmetatable({
        deskId=display.deskId, socket=display.socket, width=display.width, height=display.height,
        id="display:"..display.deskId..":"..tostring(display.socket), nodes={}, components={}, controller=nil,
        pointer=nil, pointerRevision=0
    },Runtime)
    runtime.controller = loadController(display.controller)
    runtime:switchApp(spec)
    return runtime
end

local function consumeReactiveInvalidations()
    local output = {}
    while true do
        local batch = native.consumeInvalidations()
        if #batch == 0 then break end
        local recomputed = false
        for _, inv in ipairs(batch) do
            local d = derivedByScope[inv.id]
            if d then d.recompute(); recomputed = true
            else table.insert(output,inv) end
        end
        if not recomputed then
            local extra = native.consumeInvalidations()
            for _, inv in ipairs(extra) do table.insert(output,inv) end
            break
        end
    end
    return output
end

local function pointerEventFromRaw(event)
    return {
        deskId=event[2],
        deskIndex=event[3],
        socket=event[4],
        socketName=event[5],
        moduleId=event[6],
        action=event[7],
        x=event[8],
        y=event[9],
        width=event[10],
        height=event[11],
        handler=event[12],
        u=event[13],
        v=event[14],
        deskX=event[15],
        deskY=event[16],
        deskZ=event[17]
    }
end

function ui.mount(display, app)
    assert(type(display) == "table" and display.deskId and display.socket ~= nil, "display descriptor required")
    return newRuntime(display,asApp(app))
end

function ui.run(display, app)
    local runtime = ui.mount(display,app)
    while true do
        local event = table.pack(os.pullEvent())
        local name = event[1]
        if name == "cc_aeroworks_ui_invalidated" then
            runtime:applyInvalidations(consumeReactiveInvalidations())
        elseif name == "cc_aeroworks_telemetry_added" or name == "cc_aeroworks_telemetry_changed" or name == "cc_aeroworks_telemetry_removed" then
            native.changed("telemetry:"..tostring(event[2])); native.changed("telemetry:*")
            runtime:applyInvalidations(consumeReactiveInvalidations())
        elseif name == "cc_aeroworks_console_display_input" then
            runtime:handlePointer(pointerEventFromRaw(event))
            runtime:applyInvalidations(consumeReactiveInvalidations())
        elseif runtime.spec.options and type(runtime.spec.options.onEvent) == "function" then
            local ok, err = pcall(runtime.spec.options.onEvent, table.unpack(event,1,event.n))
            if not ok then printError(tostring(err)) end
            runtime:applyInvalidations(consumeReactiveInvalidations())
        end
    end
end

local function displayRuntimeKey(deskId, socket)
    return tostring(deskId) .. ":" .. tostring(socket)
end

function ui.createSupervisor()
    local runtimes = {}
    local started = false
    local supervisor = {}

    local function descriptor(deskId,socket,controller,bootProgram)
        for _, display in ipairs(native.listDisplays()) do
            if display.deskId == deskId and display.socket == socket then
                display.controller = controller ~= nil and controller or display.controller
                display.bootProgram = bootProgram ~= nil and bootProgram or display.bootProgram
                return display
            end
        end
        return nil
    end

    local function startDisplay(display)
        if not display or not display.bootProgram or display.bootProgram == "" then return nil end
        local ok, value = pcall(loadModule,display.bootProgram)
        if not ok then printError(tostring(value)); return nil end
        return newRuntime(display,asApp(value))
    end

    local function distribute(invalidations)
        if #invalidations == 0 then return end
        for _, runtime in pairs(runtimes) do runtime:applyInvalidations(invalidations) end
    end

    function supervisor:start()
        if started then return self end
        started = true
        for _, display in ipairs(native.listDisplays()) do
            local runtime = startDisplay(display)
            if runtime then runtimes[displayRuntimeKey(display.deskId,display.socket)] = runtime end
        end
        return self
    end

    function supervisor:handle(...)
        if not started then self:start() end
        local event = table.pack(...)
        local name = event[1]
        local handled = false

        if name == "cc_aeroworks_ui_invalidated" then
            distribute(consumeReactiveInvalidations())
        elseif name == "cc_aeroworks_telemetry_added" or name == "cc_aeroworks_telemetry_changed" or name == "cc_aeroworks_telemetry_removed" then
            native.changed("telemetry:"..tostring(event[2])); native.changed("telemetry:*")
            distribute(consumeReactiveInvalidations())
        elseif name == "cc_aeroworks_display_application_changed" then
            local deskId, socket = event[2], event[4]
            local key = displayRuntimeKey(deskId,socket)
            local old = runtimes[key]
            if old then old:dispose(); runtimes[key] = nil end
            local display = descriptor(deskId,socket,event[6],event[7])
            local replacement = startDisplay(display)
            if replacement then runtimes[key] = replacement end
        elseif name == "cc_aeroworks_console_display_input" then
            local runtime = runtimes[displayRuntimeKey(event[2],event[4])]
            if runtime then
                runtime:handlePointer(pointerEventFromRaw(event))
                handled = true
            end
            distribute(consumeReactiveInvalidations())
        else
            for _, runtime in pairs(runtimes) do
                if runtime.spec.options and type(runtime.spec.options.onEvent) == "function" then
                    local ok, err = pcall(runtime.spec.options.onEvent, table.unpack(event,1,event.n))
                    if not ok then printError(tostring(err)) end
                end
            end
            distribute(consumeReactiveInvalidations())
        end
        return handled
    end

    function supervisor:hasRuntime(deskId, socket)
        return runtimes[displayRuntimeKey(deskId,socket)] ~= nil
    end

    function supervisor:dispose()
        for key, runtime in pairs(runtimes) do
            runtime:dispose()
            runtimes[key] = nil
        end
        started = false
    end

    return supervisor
end

function ui.supervise()
    local supervisor = ui.createSupervisor()
    supervisor:start()
    while true do
        local event = table.pack(os.pullEvent())
        supervisor:handle(table.unpack(event,1,event.n))
    end
end

function ui.listDisplays() return native.listDisplays() end
function ui.dependencies() return native.getDependencies() end

return ui
