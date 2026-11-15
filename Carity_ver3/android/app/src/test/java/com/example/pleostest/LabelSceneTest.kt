package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** 라벨 → 장애물 투영, 장면 생성, 거리 계산 검증 */
class LabelSceneTest {
    private val cam = DashCamera()

    private fun clip(vararg boxes: String) = LabelClip.parse(
        """{"version":2,"clip":"test","width":1280,"height":720,"sample_fps":10,"duration":1,
            "model":"yolov8n.pt","device":"cpu",
            "frames":[{"t":0.0,"objects":[${boxes.joinToString(",")}],"lanes":[]}]}""")

    private fun box(id: Int, cls: String, x1: Double, y1: Double, x2: Double, y2: Double, conf: Double = .9) =
        """{"id":$id,"cls":"$cls","conf":$conf,"box":[$x1,$y1,$x2,$y2]}"""

    @Test fun groundDistanceFollowsPinholeModel() {
        // Z = 1.3 * 0.384 * 16/9 / (y - 0.53)
        assertEquals(1.3 * .384 * 16 / 9 / .1, cam.groundDistance(.63)!!, 1e-9)
        assertNull(cam.groundDistance(.53))
        assertNull(cam.groundDistance(.40))
        // 아래쪽일수록 가깝다
        assertTrue(cam.groundDistance(.75)!! < cam.groundDistance(.60)!!)
        assertEquals(0.0, cam.lateral(.5, 10.0), 1e-12)
        assertEquals(.25 * 10 / .384, cam.lateral(.75, 10.0), 1e-9)
    }

    @Test fun leftObjectUsesRightEdgeAsInnerEdge() {
        val b = LabelBox(1, "car", .9, .05, .45, .30, .65)
        val p = LabelSceneBuilder.project(b, cam)!!
        val z = cam.groundDistance(.65)!!
        assertEquals(z, p.zNear, 1e-9)
        assertEquals(cam.lateral(.30, z), p.xInner, 1e-9)
        assertEquals(p.xInner - 1.8 / 2, p.centerX, 1e-9)
        assertFalse(p.frontal)
    }

    @Test fun rightObjectUsesLeftEdgeAsInnerEdge() {
        val p = LabelSceneBuilder.project(LabelBox(2, "car", .9, .70, .45, .98, .66), cam)!!
        val z = cam.groundDistance(.66)!!
        assertEquals(cam.lateral(.70, z), p.xInner, 1e-9)
        assertEquals(p.xInner + .9, p.centerX, 1e-9)
    }

    @Test fun centeredVehicleIsInPathAndFrontalOnlyWhenApproaching() {
        val box = LabelBox(3, "car", .9, .40, .45, .60, .62)
        val parked = LabelSceneBuilder.project(box, cam, growth = 1.0, staticGrowth = 1.0)!!
        assertTrue(parked.inPath)
        assertFalse(parked.frontal)
        val coming = LabelSceneBuilder.project(box, cam, growth = 1.3, staticGrowth = 1.0)!!
        assertTrue(coming.frontal)
        assertEquals(0.0, coming.centerX, 1e-9)
        // 자차가 움직여 정지 객체도 커지는 중이면 그보다 뚜렷하게 커져야 대향차
        assertFalse(LabelSceneBuilder.project(box, cam, growth = 1.3, staticGrowth = 1.25)!!.frontal)
    }

    @Test fun hoodReflectionIsIgnored() {
        assertNull(LabelSceneBuilder.project(LabelBox(9, "car", .9, .0, .6, 1.0, .98), cam))
    }

    @Test fun lowConfidenceFarAndWallBoxesAreIgnored() {
        assertNull(LabelSceneBuilder.project(LabelBox(4, "car", .1, .1, .5, .3, .6), cam))      // conf
        assertNull(LabelSceneBuilder.project(LabelBox(5, "car", .9, .45, .5, .48, .535), cam))  // 너무 멀다
        assertNull(LabelSceneBuilder.project(LabelBox(6, "truck", .9, .0, .05, .95, .99), cam)) // 화면 대부분
    }

    @Test fun sceneBuildsWorldAndDistancesMatchProjection() {
        val c = clip(box(1, "car", .02, .45, .33, .70), box(2, "car", .72, .45, .99, .68))
        val scene = LabelSceneBuilder.build(c, 0.0, cam)
        assertEquals(2, scene.parkedCars.size)
        val report = LabelDistanceCheck.run(scene)
        assertEquals(2, report.rows.size)
        for (row in report.rows) assertEquals(row.text(), row.expected, row.measured, LabelDistanceCheck.TOLERANCE)
        assertTrue(report.allOk)
        // 벽은 측면 객체 바깥에 있어야 측정을 가리지 않는다
        assertTrue(scene.leftWallX < scene.parkedCars[0].x - .9)
        assertTrue(scene.rightWallX > scene.parkedCars[1].x + .9)
    }

    @Test fun inPathObjectIsMeasuredFromTheFreeSide() {
        // 경로에 걸친 오른쪽 객체: 자차를 왼쪽으로 0.5m 여유 두고 통과시켜 측정
        val c = clip(box(1, "car", .45, .45, .80, .68))
        val scene = LabelSceneBuilder.build(c, 0.0, cam)
        assertTrue(scene.projected.single().inPath)
        val row = LabelDistanceCheck.run(scene).rows.single()
        assertEquals(.5, row.expected, 1e-9)
        assertEquals(row.text(), .5, row.measured, LabelDistanceCheck.TOLERANCE)
    }

    @Test fun momentsPreferFramesWithCloseObjects() {
        val json = """{"clip":"m","frames":[
            {"t":0.0,"objects":[]},
            {"t":5.0,"objects":[${box(1, "car", .02, .45, .35, .75)}]},
            {"t":9.0,"objects":[${box(2, "car", .42, .5, .58, .62)}]}]}"""
        val m = LabelSceneBuilder.suggestMoments(LabelClip.parse(json))
        assertEquals(listOf(5.0, 9.0), m.map { it.t })
    }

    /** 실제 라벨 파일(assets 동봉)로 전체 경로 검증: 투영 → 장면 → 거리 → 판단 → 경로 */
    @Test fun bundledRealLabelsProduceConsistentScenes() {
        val dir = listOf(File("src/main/assets/label_samples"), File("app/src/main/assets/label_samples"))
            .firstOrNull { it.isDirectory } ?: return
        val index = MiniJson.arr(MiniJson.obj(MiniJson.parse(File(dir, "index.json").readText()))["samples"])
        assertTrue(index.isNotEmpty())
        for (entry in index.map { MiniJson.obj(it) }) {
            val clip = LabelClip.parse(File(dir, MiniJson.str(entry["file"])).readText())
            val t = MiniJson.num(entry["t"])
            val scene = LabelSceneBuilder.build(clip, t)
            assertTrue("${clip.clip} 장애물 없음", scene.parkedCars.isNotEmpty())
            val world = scene.toWorld()
            val plan = CorridorPlanner.plan(world, scene.frontal)
            val report = LabelDistanceCheck.run(scene, plan)
            assertTrue("${clip.clip}: ${report.rows.map { it.text() }}", report.allOk)
            assertTrue(plan.points.isNotEmpty())
            // 경로 위 모든 점은 접촉 없음
            assertTrue("${clip.clip} 경로 접촉", plan.points.all { it.min > 0.0 })
            val expected = MiniJson.str(entry["expect"])
            if (expected.isNotEmpty()) assertEquals(clip.clip, expected, plan.outcome.name)
        }
    }

    @Test fun cornerCaseFrameAtNearestTimeIsUsed() {
        val json = """{"clip":"n","frames":[{"t":1.0,"objects":[]},{"t":1.1,"objects":[${box(1, "car", .7, .45, .99, .7)}]}]}"""
        val scene = LabelSceneBuilder.build(LabelClip.parse(json), 1.12)
        assertEquals(1.1, scene.t, 1e-9)
        assertEquals(1, scene.parkedCars.size)
        assertTrue(abs(scene.parkedCars[0].y - (LabelSceneBuilder.EGO_START_Y + LabelSceneBuilder.LEAD + LabelSceneBuilder.CAMERA_FORWARD +
            cam.groundDistance(.7)!! + 2.2)) < 1e-9)
    }
}
