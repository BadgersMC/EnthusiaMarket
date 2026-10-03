package net.badgersmc.em.application

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/** Normalize only entity identity/timers in creature containers, preserving all other item data. */
internal object SpecialItemMatch {
    private val incidental = setOf("UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation",
        "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown", "Brain", "HuntingCooldown",
        "TicksSincePollination", "CannotEnterHiveTicks", "TicksSinceSting", "FlowerPos", "HivePos",
        "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "InLove", "LoveCause", "ForcedAge")
        .flatMap { listOf(it, it.lowercase()) }.toSet()

    fun matches(a: ByteArray, b: ByteArray): Boolean = runCatching {
        normalized(read(a)) == normalized(read(b))
    }.getOrDefault(false)

    private fun read(bytes: ByteArray): Any {
        val raw = ByteArrayInputStream(bytes)
        val stream = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) GZIPInputStream(raw) else raw
        return DataInputStream(stream).use { input ->
            val type = input.readUnsignedByte()
            require(type == 10)
            readString(input)
            payload(input, type, 0)
        }
    }

    @Suppress("CyclomaticComplexMethod") // NBT protocol has twelve payload tag types.
    private fun payload(input: DataInputStream, type: Int, depth: Int): Any {
        require(depth < 64)
        return when (type) {
            1 -> input.readByte()
            2 -> input.readShort()
            3 -> input.readInt()
            4 -> input.readLong()
            5 -> input.readFloat()
            6 -> input.readDouble()
            7 -> List(size(input)) { input.readByte() }
            8 -> readString(input)
            9 -> { val child = input.readUnsignedByte(); List(size(input)) { payload(input, child, depth + 1) } }
            10 -> buildMap<String, Any> {
                while (true) {
                    val child = input.readUnsignedByte()
                    if (child == 0) break
                    val name = readString(input)
                    put(name, payload(input, child, depth + 1))
                }
            }
            11 -> List(size(input)) { input.readInt() }
            12 -> List(size(input)) { input.readLong() }
            else -> error("Unsupported NBT type $type")
        }
    }

    private fun size(input: DataInputStream): Int = input.readInt().also { require(it in 0..1_000_000) }
    private fun readString(input: DataInputStream): String = ByteArray(input.readUnsignedShort()).also { input.readFully(it) }.toString(Charsets.UTF_8)

    internal fun normalized(value: Any, entity: Boolean = false, bees: Boolean = false,
        components: Boolean = false, depth: Int = 0,
    ): Any = when (value) {
        is Map<*, *> -> value.entries.filterNot {
            entity && it.key in incidental || bees && it.key in setOf("ticks_in_hive", "min_ticks_in_hive", "TicksInHive", "MinOccupationTicks")
        }.associate { (key, item) ->
            val nextEntity = entity || components && key == "minecraft:bucket_entity_data" || bees && key in setOf("entity_data", "EntityData")
            val nextBees = bees || components && key == "minecraft:bees"
            val normalized = if (entity && key in setOf("Age", "age") && item is Number)
                if (item.toLong() < 0) -1 else 0 // Baby/adult distinction, without exact age/breeding timers.
            else normalized(requireNotNull(item), nextEntity, nextBees, depth == 0 && key == "components", depth + 1)
            key to normalized
        }
        is List<*> -> value.map { normalized(requireNotNull(it), entity, bees, false, depth + 1) }
        else -> value
    }
}
