package com.unibo.s3.testbed.samples

import box2dLight.{ConeLight, PointLight, RayHandler}
import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.{Color, OrthographicCamera}
import com.badlogic.gdx.math.{MathUtils, Vector2}
import com.badlogic.gdx.{Gdx, InputMultiplexer}
import com.unibo.s3.main_system.characters.steer.MovableEntity
import com.unibo.s3.main_system.characters.steer.collisions.Box2dProxyDetectorsFactory
import com.unibo.s3.main_system.util.ScaleUtils
import com.unibo.s3.testbed.Testbed
import com.unibo.s3.testbed.ui.LogMessage

import scala.collection.JavaConversions._

class LightingSystemTest extends EntitySystemModule {


  private var rayHandler: RayHandler = _
  private var cam: OrthographicCamera = _
  private var torches = List[ConeLight]()
  private var lights = List[PointLight]()

  private val LIGHT_WHITE = new Color(1.0f, 1.0f, 1.0f, 0.5f)
  private var lightEditorEnabled = true
  private var renderLightsAfterBodies = true

  private val b2d = new Box2dModule()
  submodules :+= b2d


  private def testUpdateTorches(characters: List[MovableEntity[Vector2]]) = {
    for (i <- characters.indices) {
      if (torches.size == characters.size) {
        val t = torches(i)
        val c = characters(i)
        t.setPosition(c.getPosition)
        t.setDirection((c.getOrientation * MathUtils.radiansToDegrees )  + 90)
      }
    }
  }

  private def testCreateTorches(characters: List[MovableEntity[Vector2]]) = {
    characters.foreach(c => {
      val t = new ConeLight(rayHandler, 65, LIGHT_WHITE, 15f,
        c.getPosition.x, c.getPosition.y,
        (c.getOrientation * MathUtils.radiansToDegrees) + 90, 25f)
      //t.setSoft(false)
      //t.setSoftnessLength(0f)
      torches :+= t
    })
  }

  override def update(dt: Float): Unit = {
    super.update(dt)
    val e = getEntities.toList
    if (getEntities.size > 0 && torches.size <= 0) testCreateTorches(e)
    testUpdateTorches(e)
  }

  override def init(owner: Testbed): Unit = {
    super.init(owner)
    cam = owner.getCamera
    rayHandler = new RayHandler(b2d.getWorld,
      Gdx.graphics.getWidth, Gdx.graphics.getHeight)
  }

  override def setup(f: (String) => Unit): Unit = {
    super.setup(f)
    b2d.loadWorld("world0.txt")
    rayHandler.setWorld(b2d.getWorld)
    collisionDetector = new Box2dProxyDetectorsFactory(b2d.getWorldActorRef)
      .newRaycastCollisionDetector()
    RayHandler.setGammaCorrection(false)
    RayHandler.useDiffuseLight(true)
    this.rayHandler.setBlur(true)
    this.rayHandler.setBlurNum(1)
    this.rayHandler.setShadows(true)
    this.rayHandler.setCulling(true)
    rayHandler.setAmbientLight(new Color(.1f, .1f, .1f, .1f))
    loadLights("lights.txt")
  }

  override def render(shapeRenderer: ShapeRenderer): Unit = {
    if (renderLightsAfterBodies) {
      super.render(shapeRenderer)
    }

    rayHandler.setCombinedMatrix(
      cam.combined.cpy().scl(ScaleUtils.getPixelsPerMeter.toFloat), 0, 0,
      cam.viewportWidth, cam.viewportHeight)
    rayHandler.updateAndRender()
    rayHandler.useDefaultViewport()

    if (!renderLightsAfterBodies) {
      super.render(shapeRenderer)
    }
  }

  override def resize(newWidth: Int, newHeight: Int): Unit = {
    super.resize(newWidth, newHeight)
    rayHandler.useCustomViewport(cam.position.x.toInt, cam.position.y.toInt,
      cam.viewportWidth.toInt, cam.viewportHeight.toInt)
  }

  private def saveLights(name: String) = {
    val w = Gdx.files.local(name)
    w.writeString("", false)
    lights.foreach(l => w.writeString(l.getPosition.x + ":" + l.getPosition.y + "\n", true))
  }

  private def loadLights(name: String) = {
    val w = Gdx.files.internal(name)
    val c = w.readString()
    if (!c.isEmpty) {
      c.split("\n")
        .map(l => l.split(":").map(x => x.toFloat))
        .foreach(p =>
          new PointLight(rayHandler, 64, LIGHT_WHITE, 20, p(0), p(1)))
    }
  }

  override def cleanup(): Unit = {
    super.cleanup()
  }

  override def touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean = {
    super.touchUp(screenX, screenY, pointer, button)
    if (lightEditorEnabled) {
      val mouseWorldPos = owner.screenToWorld(new Vector2(screenX, screenY))
      mouseWorldPos.scl(ScaleUtils.getMetersPerPixel)
      owner.getLogger (LogMessage("LightSystem",
        "New Light created @" + mouseWorldPos, Color.LIGHT_GRAY))
      lights :+= new PointLight(rayHandler, 64, LIGHT_WHITE, 20, mouseWorldPos.x, mouseWorldPos.y)
    }
    false
  }

  override def keyUp(keycode: Int): Boolean = {
    super.keyUp(keycode)
    if(keycode == Keys.Y) lightEditorEnabled = !lightEditorEnabled
    if(keycode == Keys.T) enable(!enabled)
    if(keycode == Keys.D) RayHandler.useDiffuseLight(!RayHandler.isDiffuse)
    if(keycode == Keys.R) renderLightsAfterBodies = !renderLightsAfterBodies
    false
  }

  override def attachInputProcessors(inputMultiplexer: InputMultiplexer): Unit = {
    super.attachInputProcessors(inputMultiplexer)
  }
  override def getKeyShortcuts: Option[Map[String, String]] = {
    var s = Map(
      "y" -> "enable adding lights.",
      "t" -> "toggle lighting system",
      "r" -> "switch pre/post lights rendering",
      "d" -> "toggle diffuse lights",
      "mouse-left" -> "add light"
    )

    val t = super.getKeyShortcuts
    if (t.isDefined) {
      t.get.foreach(k => s += k)
    }
    Option(s)
  }

}