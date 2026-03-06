# Movable Collision Platforms & Client Interpolation — Plan

**Goal:** Colossal creatures that players can walk on and build on, with movable collision that moves with the creature and (ideally) smooth client-side presentation.  
**Constraint:** No client modding — all logic must work with the vanilla client via server behaviour and protocol.

This document outlines options discovered from the Hytale server source, the Worldbearers mod, and inspiration from the Create mod (Minecraft). It is a **plan only**; no code is implemented yet.

---

## 1. Current State (Relevant Hytale Behaviour)

### 1.1 Collision (Server)

- **CollisionModule** is the central entry point: `findCollisions(Box, pos, velocity, CollisionResult, ComponentAccessor)` and `findIntersections(...)`, `validatePosition(...)`.
- **Block collision:** World is static. Blocks are at fixed (x,y,z). There is **no built-in “moving platform”** block type. Collision uses:
  - **Box** (AABB), **BlockBoundingBoxes** per block type, **BoxBlockIntersectionEvaluator** / **MovingBoxBoxCollisionEvaluator**, **CollisionMath** (ray/AABB, swept AABB, overlap codes).
- **Character (entity) collision:** `findCharacterCollisions(pos, v, result, componentAccessor)` uses `result.getCollisionEntities()` — a list of entities to test against. It is only invoked when `findCollisions` is called with “checking for character collisions” enabled. That list is **populated by the caller** (e.g. from a spatial query); it is not filled by the collision module itself.
- **Who uses character collision?** NPC motion controllers (e.g. **MotionControllerWalk**) use `CollisionModule.findCollisions` with a collision box and get block and (if configured) character collisions. **Player movement** in **PlayerProcessMovementSystem** uses **findIntersections** (blocks only) for triggers/damage; it does **not** call `findCollisions` with character collision. So by default, **the player does not collide with other entities** in the standard movement pipeline; the client sends position and the server applies it via `Player.moveTo` and stores the movement delta in **CollisionResultComponent** for velocity sampling.

So today:

- Your **body-part entities** (Giant_BodyPart NPCs with **BoundingBox**) are moved every tick by **GiantBodyPartUpdateSystem** to follow the giant. They are normal collidable NPCs.
- The **player** movement path does not run character-vs-entity collision in the core movement step. So **standing on a body part** is not yet enforced by the engine’s player collision.

### 1.2 Player Position and Sync

- **Server:** Position lives in **TransformComponent**. The client sends **AbsoluteMovement** / **RelativeMovement**; the server calls **Player.moveTo**, which updates **TransformComponent** and **addLocationChange** (accumulates delta in **CollisionResultComponent** for velocity sampling).
- **Server → client:** **TransformSystems.EntityTrackerUpdate** compares **TransformComponent** to **sentTransform**; when position/rotation change, it sends **TransformUpdate(sentTransform)** to viewers. So sync is **state-based** (current position/rotation), not interpolated on the server. The **client** likely interpolates when it receives **TransformUpdate** (we have no client source).

### 1.3 Mount System

- **MountedComponent** (on rider): `mountedToEntity` or `mountedToBlock`, **MountController**, **attachmentOffset** (Vector3f).
- **HandleMountInput** (runs before **ProcessPlayerInput**): For **entity** mounts, the **target** of movement is the mount entity. So when the player sends **RelativeMovement** / **AbsoluteMovement**, the server applies it to **both** the player and the mount’s **TransformComponent**. Rider and mount move together; the mount is “driven” by the player.
- There is **no** built-in system that, each tick, sets rider position = mount position + offset. So when the **mount** moves on its own (e.g. our body part moved by **GiantBodyPartUpdateSystem**), the rider’s position is **not** automatically updated by the mount system.
- The client receives **MountedUpdate** (mount entity id, attachment offset, controller type), so the client can render the rider relative to the mount (and likely interpolate the mount).

---

## 2. Options for Collision (Standing on the Creature)

We need the player (and optionally other entities) to **collide** with the moving body-part boxes so they can stand and walk on them.

### Option A: Rely on existing entity collision for the player

- **Idea:** Ensure the player’s movement pipeline uses **findCollisions** with **character collision** enabled and **setCollisionEntities** populated with nearby entities (including our body-part refs).
- **Reality:** The core **PlayerProcessMovementSystem** and the path that applies **AbsoluteMovement** / **RelativeMovement** do **not** call **findCollisions** for the player; they accept the client position and apply it. So the vanilla player path does not resolve player vs entity collisions. We would need to **inject** a step that:
  - Before or after applying client movement, runs **findCollisions** (with a suitable box and world + character list) and corrects the player position (e.g. slide or reject).
- **Challenge:** Finding a safe injection point (system order, dependencies) and populating **CollisionResult** with the right entity list (e.g. from **TangiableEntitySpatialSystem** / **CollisionModule.getTangibleEntitySpatialResourceType()**) without breaking other behaviour. The player’s movement is currently “client position → moveTo”; adding a collision step might require a “proposed position → findCollisions → corrected position → moveTo” flow.

### Option B: Custom “standing on platform” detection only (no engine character collision)

- **Idea:** Do **not** use the engine’s character collision. Instead, implement our own rule: “if the player’s feet AABB would intersect a body-part AABB at the body part’s **current** position, treat them as standing on that part.” Use this only for:
  - Deciding **who is on which platform** (for applying platform carry), and
  - Optionally **rejecting** clearly invalid positions (e.g. player inside solid part).
- **Collision:** We don’t make the player “collide” with the body part in the engine sense; we only use our own AABB test for game logic (platform carry, maybe anti-cheat). The player could still be moved through the part by the client until we correct it (e.g. by applying platform delta or teleporting).
- **Pros:** Full control; no dependency on engine character collision.  
- **Cons:** Player might briefly clip or be accepted in bad positions if we don’t add our own correction.

### Option C: Custom AABB collision layer for “platform” entities

- **Idea:** Implement a small custom “collision world” that contains only our body-part AABBs (and maybe other future moving platforms). Each tick:
  - Update this layer from current body-part **TransformComponent** + **BoundingBox**.
  - When processing player (or entity) movement, resolve against this layer (e.g. swept AABB or step + validate) and then apply the result (e.g. correct position, or add platform velocity).
- **Pros:** Clear separation; same patterns as **CollisionMath** (AABB, swept AABB); can support multiple giants and other moving platforms later.  
- **Cons:** Duplicates some collision logic; we must run at the right time relative to player movement and platform updates.

### Option D: Hybrid — use engine NPC collision where possible, custom only for “carry”

- **Idea:** If we can hook the player into character collision (Option A), then the player would naturally collide with body-part entities. We would still add **custom platform-carry** logic (see below) so that when the player is “on” a part, we add the part’s delta to the player. If we cannot hook the player cleanly, fall back to Option B or C for “who is on the platform” and optionally for correction.

---

## 3. Options for “Platform Carry” (Moving with the Creature)

When a player (or entity) is standing on a moving body part, we must move them with the part each tick.

### Option 1: Delta application (no mount)

- **Idea:** Each tick, for each body part, compute **delta = current position − previous position**. For each player (or entity) we consider “on” that part (from Option A/B/C above), add **delta** to their **TransformComponent** position (and optionally add the same delta to **Velocity** or leave velocity unchanged depending on desired feel).
- **Implementation:** A system that runs **after** **GiantBodyPartUpdateSystem**, that:
  - Knows which body parts exist and their previous positions (store last position per body-part ref).
  - Determines “standing on” via your chosen collision/detection (A/B/C).
  - Applies delta to each rider’s transform (and optionally notifies or marks for velocity sampling).
- **Client:** The server sends **TransformUpdate** for the player every time their position changes. The client will receive the new position each tick when the platform moves; it may interpolate between these. No client change.
- **Pros:** Simple conceptually; works with “no mount” and any detection method.  
- **Cons:** No use of the mount protocol; client might not interpolate the player as smoothly as “rider attached to mount” if it treats the player as a free-moving entity.

### Option 2: Use MountedComponent and treat body part as mount

- **Idea:** When the player is considered “on” a body part, add **MountedComponent** with `mountedToEntity = bodyPartRef` and an **attachmentOffset** (e.g. in body-part local space). The **client** already receives **MountedUpdate** and can render the rider at mount position + offset; it likely interpolates the mount (body part) and thus the rider smoothly.
- **Gap:** The built-in **HandleMountInput** moves the **mount** when the player sends movement. We need the opposite: the **body part** is moved by **GiantBodyPartUpdateSystem**, and the **rider** must follow. So we need:
  1. **Rider sync:** A system that runs after **GiantBodyPartUpdateSystem** and, for every entity with **MountedComponent** whose `mountedToEntity` is one of our body-part refs, sets **rider position = body part position + rotation(attachmentOffset)** (using the body part’s current transform). That way the rider stays on the part as it moves.
  2. **Input handling:** When the rider sends **RelativeMovement** / **AbsoluteMovement**, we must **not** move the body part. Instead we should update **attachmentOffset** (in body-part local space) so that the rider “walks” relative to the part. This may require a **custom mount controller** or a dedicated system that runs before/after **HandleMountInput** and handles our body-part mounts differently (e.g. apply movement to offset only, and then sync rider from mount + offset).
- **Pros:** Reuses mount protocol; client may already interpolate rider with mount.  
- **Cons:** Need to implement rider-sync and custom input handling for “mount type = body part”; must store offset in local space and transform by body part rotation.

### Option 3: Hybrid — delta for position, mount for display only (if feasible)

- **Idea:** Use Option 1 (delta) for authoritative position, but also add **MountedComponent** (or a similar protocol hint) so the client **displays** the player as attached to the body part. This is speculative: the protocol might not support “display as mounted but position is server-driven” without the server also sending position; then we’d be back to Option 2 or 1.

### Option 4: “Fake” block collision (not recommended)

- **Idea:** Temporarily place or toggle block types under the player so the engine’s block collision “holds” them.  
- **Reality:** Block positions are fixed in the world; we cannot move blocks with the giant. So we cannot implement moving platforms as real blocks. Only worth mentioning to rule it out.

---

## 4. Client Interpolation (No Client Mod)

We cannot change client code. We can only influence what the server sends and how often.

### What the server sends

- **TransformUpdate** for entities (including the player and body-part NPCs): position + rotation when they change.
- **MountedUpdate** for the rider: mount entity id + attachment offset.

### Likely client behaviour (inferred)

- The client probably **interpolates** between the last two (or more) **TransformUpdate** positions for entities. So when we move the body part every tick, the client gets a new position every tick and can interpolate the **body part** smoothly.
- For the **player**:
  - **If we use Option 1 (delta):** We write the player’s position every tick (platform carry). The server sends **TransformUpdate** every tick. The client will receive discrete positions and likely interpolate between them — so movement should look reasonably smooth as long as tick rate is sufficient.
  - **If we use Option 2 (mount):** The client is told “player is mounted to entity X with offset Y.” If the client derives the rider’s **render** position from the mount’s interpolated position + offset, then we get smooth interpolation “for free” as long as we keep the rider’s **server** position in sync with mount + offset (so we don’t get rubber-banding).

### “Tricking” interpolation

- **Higher tick rate:** If the server ticks more often when giants are present, we send position updates more often and the client has more points to interpolate. May not be configurable per-scenario.
- **Mount trick:** Using **MountedComponent** (Option 2) is the main way to leverage the client’s existing “rider follows mount” behaviour; the client likely already interpolates the mount, so the rider follows smoothly.
- **Avoid teleporting:** Prefer adding **deltas** to the player instead of large teleports when they’re on a platform, so the client sees small, interpolatable steps rather than jumps.

### What we cannot do

- We cannot add new client-side interpolation logic or new packet types that the client understands. We are limited to existing protocol (e.g. **TransformUpdate**, **MountedUpdate**).

---

## 5. Create Mod (Minecraft) — Inspiration

- Create uses **contraptions**: structures of blocks that move as one (e.g. with Mechanical Piston, Bearing). The key ideas are:
  - **Assembly:** Detect connected blocks and form a single logical “entity” or structure.
  - **Movement:** The whole structure moves (translate/rotate) in the world.
  - **Collision:** Entity-vs-structure collision is handled so entities on the structure move with it (and can stand/walk on it).
- In Minecraft, this is often done by:
  - Tracking which entities are “on” the contraption (e.g. AABB overlap or “last support”).
  - Each tick, applying the contraption’s **motion delta** to those entities (similar to our Option 1).
  - Sometimes using **mount** or **passenger** relationships so the client renders entities attached to the moving structure.
- **Takeaway:** The same two ideas apply in Hytale: (1) **detect “on platform”** (AABB or engine collision), (2) **apply platform delta** each tick. Optionally use the mount system so the client treats the player as attached (Option 2).

---

## 6. Building on the Giant

If “building on” the creature means placing blocks in the world that are **attached** to the creature and move with it:

- The engine has **no** “moving block” or “block attached to entity” concept. Blocks are in chunk storage at fixed coordinates.
- **Options (high level):**
  - **Virtual building:** Store “placements” in mod state (e.g. relative to giant or body part). Render them in a custom way (if we had client mod) or represent them with entities (e.g. invisible entities or item frames). Without client mod, “blocks” that move with the giant would have to be represented as **entities** (e.g. small display entities or props) that we move with the giant, and actual block placement might be disallowed or limited to world coordinates (so they don’t move with the giant).
  - **Snapshot and move:** When the giant moves, “move” built blocks by removing them at old positions and placing at new positions (relative to giant). This would be expensive and could cause flicker; only viable for very small build zones or special cases.

Building is left as a follow-up; the rest of this plan focuses on **collision** and **platform carry**.

---

## 7. Recommended Direction (Summary)

- **Collision**
  - **Short term:** Implement **Option B** (custom “standing on platform” detection with AABB) so we have a clear definition of “player on body part” for carry logic, without depending on engine character collision.
  - **Medium term:** If possible, explore **Option A** (inject player into character collision with body parts) so the engine can reject invalid positions and we don’t rely only on custom checks.
  - **Alternative:** **Option C** (custom AABB layer) if we want a single, reusable “moving platform” abstraction for multiple creatures and future content.

- **Platform carry**
  - **Preferred:** **Option 2** (mount body part + rider sync + custom input for offset). This gives the best chance of smooth client interpolation (rider rendered relative to interpolated mount) and reuses protocol. Implement rider-sync after **GiantBodyPartUpdateSystem** and custom handling for movement that updates **attachmentOffset** instead of moving the body part.
  - **Fallback:** **Option 1** (delta application). Simpler; no mount protocol; client still gets **TransformUpdate** every tick and can interpolate.

- **Client interpolation**
  - Rely on **TransformUpdate** frequency and, if using Option 2, on the client’s mount rendering. Avoid large teleports; prefer small deltas. No way to “tell the client” new interpolation rules without client mod.

- **Order of operations (tick)**
  1. **GiantBodyPartUpdateSystem:** Update body part positions from giant transform.
  2. **Platform carry system:** Either (Option 1) apply body-part deltas to riders, or (Option 2) set rider position = body part position + rotated(attachmentOffset), and handle input by updating attachmentOffset for body-part mounts.
  3. Player input handling for non-mounted or normal mounts unchanged; for body-part mounts, custom logic so movement updates offset, not body part position.

---

## 8. Open Points / Future Work

- **Body part motion controller:** The body part NPC uses a motion controller for movement. With **Walk**, the engine applies gravity so the **NPC model** falls while our systems keep the **collision box** (TransformComponent) correct — the rider then appears to fall because they are drawn attached to the falling model. Using **Idle** in the role’s `MotionControllerList` (no gravity) avoids this. If the engine does not support Idle or it behaves poorly, alternatives are: (1) use the smallest valid Walk values (e.g. Gravity 0.01) so fall is negligible, or (2) implement a **custom motion controller** (e.g. “Platform” or “Kinematic”) that applies no movement and is driven only by our systems (position bound to the platform AABB). Custom controllers require the engine to support registration of additional motion controller types.
- Exact **system order** and **dependencies** (before/after **ProcessPlayerInput**, **HandleMountInput**, **TransformSystems**, etc.) so platform carry and optional mount behaviour don’t conflict.
- **Intangible** or **Invulnerable** flags on body-part entities so they don’t interact with combat/knockback in unwanted ways while still colliding or being detected.
- **Multiple players** on the same or different body parts; **NPCs** or other entities as riders; **dismount** when the giant dies or the part is removed.
- **Building on the giant:** Representing and moving “attached” blocks as entities or virtual placements without client mod.
- **Performance:** Spatial queries (e.g. “which players are near this body part?”) and per-tick AABB checks; scale with many giants and many players.

This plan should give a solid basis to implement movable collision platforms and platform carry within server-only constraints and to explore client interpolation within the existing protocol.
