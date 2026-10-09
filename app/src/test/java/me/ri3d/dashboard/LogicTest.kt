package me.ri3d.dashboard

import me.ri3d.dashboard.VehicleState.Door
import me.ri3d.dashboard.VehicleState.Reading
import me.ri3d.dashboard.VehicleState.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class LogicTest {
    @Test fun favoritesRespectCapacityAndOrder() {
        val a = listOf("radio", "aa", "call", "files")
        assertEquals(a + "x/.X", Favorites.add(a, "x/.X", Prefs.max(false)))
        assertEquals(a, Favorites.add(a, "x/.X", Prefs.max(true)))             // B is full at 4
        assertEquals(a, Favorites.add(a, "aa", 5))                              // no duplicates
        assertEquals(listOf("radio", "call", "files"), Favorites.remove(a, "aa"))
        assertEquals(listOf("radio", "aa", "y/.Y", "files"), Favorites.replace(a, 2, "y/.Y"))
        assertEquals(a, Favorites.replace(a, 2, "aa"))                          // already pinned elsewhere
        assertEquals(listOf("aa", "radio", "call", "files"), Favorites.move(a, 1, -1))
        assertEquals(a, Favorites.move(a, 0, -1))                               // end of the row
        assertEquals(a, Favorites.move(a, 3, 1))
    }

    @Test fun vehicleFreshnessAndDoors() {
        val v = VehicleState()
        assertEquals(Status.NO_DATA, v.status(0))
        v.put(Reading.FUEL, 62.0, 1000)
        assertEquals(Status.LIVE, v.status(2000))
        assertEquals(VehicleState.STALE_MS - 1000, v.nextExpiry(2000))
        assertEquals(Status.STALE, v.status(1000 + VehicleState.STALE_MS))
        v.put(Reading.FUEL, 61.0, 20_000)                                       // recovery
        assertEquals(Status.LIVE, v.status(20_500))
        v.put(Reading.RANGE, -5.0, 20_000)                                      // malformed is unavailable, not zero
        assertNull(v.value(Reading.RANGE))
        assertFalse(v.allClosed())                                              // unknown doors are not "closed"
        v.putDoor(Door.FRONT_LEFT, false, 20_000); v.putDoor(Door.FRONT_RIGHT, false, 20_000)
        v.putDoor(Door.REAR_LEFT, false, 20_000)
        assertFalse(v.allClosed())
        v.putDoor(Door.REAR_RIGHT, false, 20_000)
        assertTrue(v.allClosed())
        v.putDoor(Door.REAR_LEFT, true, 21_000)
        assertEquals(listOf(Door.REAR_LEFT), v.openDoors())
        assertEquals(Status.DOOR_OPEN, v.status(21_500))
    }

    @Test fun widgetSlotsParseSafely() {
        assertEquals(WidgetSlots.DEFAULT, WidgetSlots.parse(null))
        assertEquals(WidgetSlots.DEFAULT, WidgetSlots.parse("nonsense"))
        val mine = listOf(Metric.SOC, Metric.SOC, Metric.SPEED, Metric.TRIP, Metric.DOORS, Metric.CABIN, Metric.OUTSIDE)
        assertEquals(mine, WidgetSlots.parse(WidgetSlots.format(mine)))                // round trip
        // Doors cannot be the large value, range cannot drive the bar: those places fall back.
        val bad = WidgetSlots.parse("doors,range,speed,doors,cabin,speed,odometer")
        assertEquals(Metric.OUTSIDE, bad[WidgetSlots.MAIN])
        assertEquals(Metric.FUEL, bad[WidgetSlots.BAR])
        assertEquals(Metric.SPEED, bad[WidgetSlots.BELOW])
    }

    @Test fun svgArcsAreCircles() {
        // The AA icon's ring: two half-circle arcs of radius 9 around (12, 12).
        val pts = ArrayList<FloatArray>()
        var cx = 0f; var cy = 0f
        SvgPath.parse("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", object : SvgPath.Sink {
            override fun move(x: Float, y: Float) { cx = x; cy = y }
            override fun line(x: Float, y: Float) { cx = x; cy = y }
            override fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
                // Midpoint of the Bézier segment must lie on the circle.
                val mx = 0.125f * cx + 0.375f * x1 + 0.375f * x2 + 0.125f * x
                val my = 0.125f * cy + 0.375f * y1 + 0.375f * y2 + 0.125f * y
                pts.add(floatArrayOf(mx, my, x, y)); cx = x; cy = y
            }
            override fun close() {}
        })
        assertEquals(4, pts.size)
        for (p in pts) assertEquals(9.0, hypot(p[0] - 12.0, p[1] - 12.0), 0.01)
        assertEquals(3f, cx, 1e-4f); assertEquals(12f, cy, 1e-4f)
    }

    @Test fun everyIconParses() {
        val sink = object : SvgPath.Sink {
            override fun move(x: Float, y: Float) {}
            override fun line(x: Float, y: Float) {}
            override fun cubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {}
            override fun close() {}
        }
        for (f in Icons::class.java.declaredFields) {
            if (f.type != String::class.java) continue
            f.isAccessible = true
            SvgPath.parse(f.get(Icons) as String, sink)
        }
    }
}
