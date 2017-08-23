package com.unibo.s3.testbed.testbed_modules.future

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Tree.Node
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent
import com.badlogic.gdx.scenes.scene2d.utils.{ChangeListener, ClickListener}
import com.badlogic.gdx.scenes.scene2d.{Actor, InputEvent, Stage}
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.badlogic.gdx.{Gdx, InputMultiplexer}
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.util.ToastManager
import com.kotcrab.vis.ui.widget.toast.Toast
import com.kotcrab.vis.ui.widget.{VisLabel, VisTree, _}
import com.unibo.s3.main_system.AbstractMainApplication
import com.unibo.s3.testbed.Testbed
import com.unibo.s3.testbed.testbed_modules.future.ui.KeyHelpTable


case class FutureTestbed() extends AbstractMainApplication with Testbed {

  private[this] var currentSample: Option[Sample] = None
  private[this] var nextSample: Option[Sample] = None
  private[this] var inputMultiplexer: InputMultiplexer = _
  private[this] var gui: Stage = _
  private[this] val defaultPaneSize = 200f
  private[this] val currentSampleMenuWidth = 250f
  private[this] var viewport: Cell[_ <: Actor] = _
  private[this] var loadingBar: VisProgressBar = _
  private[this] var loadingLog: VisLabel = _
  private[this] var samplePane: VisWindow = _
  private[this] var lastTransitionOut = false
  private[this] var loadingFinished = true
  private[this] var toastManager: ToastManager = _
  private[this] var currSampleName: String = _
  private[this] var menuBar: MenuBar = _
  private[this] var currSampleKeybindings: Option[KeyHelpTable] = None

  def resizeGui(): Unit = {
    viewport.width(Gdx.graphics.getWidth - (currentSampleMenuWidth + defaultPaneSize))
  }

  private def addSample(node: Node, sampleName: String): Unit = {
    val lab2 = new VisLabel(sampleName)
    lab2.addListener(new ClickListener(){
      override def clicked(event: InputEvent, x: Float, y: Float): Unit = {
        super.clicked(event, x, y)
        currSampleName = lab2.getText.toString
        nextSample = matchSample(currSampleName)
      }
    })
    node.add(new Node(lab2))
  }

  private def createMenu() = {
    val fileMenu = new Menu("File")
    val helpMenu = new Menu("Help")

    val testbedKeysHelp = new MenuItem("Testbed shortcuts", new ChangeListener {
      override def changed(event: ChangeEvent, actor: Actor): Unit = {
        val keys = new KeyHelpTable(true)
        keys.addKeyBinding("p", "pause.")
        keys.addKeyBinding("h", "hide left pane.")
        keys.addKeyBinding("q", "zoom in")
        keys.addKeyBinding("a", "zoom out")
        keys.addKeyBinding("arrow-left", "move camera left")
        keys.addKeyBinding("arrow-right", "move camera right")
        keys.addKeyBinding("arrow-up", "move camera up")
        keys.addKeyBinding("arrow-down", "move camera down")
        keys.addKeyBinding(List("ctrl", "a"), "test combination")
        keys.addKeyBinding(List("ctrl", "mouse-left"), "test combination + mouse")

        val t = new Toast("dark", keys)
        toastManager.show(t)
        centerToast(t)
      }
    })

    val sampleKeysHelp = new MenuItem("Sample shortcuts", new ChangeListener {
      override def changed(event: ChangeEvent, actor: Actor): Unit = {
        val t = currSampleKeybindings match {
          case Some(keys) =>
            new Toast("dark", keys)
          case _ =>
            val tab = new VisTable()
            tab.add(new VisLabel("No provided keybindigs!"))
            new Toast("dark", tab)
        }
        toastManager.show(t)
        centerToast(t)
      }
    })

    helpMenu.addItem(testbedKeysHelp)
    helpMenu.addItem(sampleKeysHelp)
    menuBar.addMenu(fileMenu)
    menuBar.addMenu(helpMenu)

  }

  private def initGui() = {
    VisUI.load()
    toastManager = new ToastManager(gui)
    toastManager.setAlignment(Align.bottomRight)

    val eastPane = new VisWindow("")

    val blue = new Color(2/255f, 179/255f, 255/255f, 1f)
    eastPane.getTitleLabel.setText("Testbed menu")
    eastPane.getTitleLabel.setColor(blue)

    val tree = new VisTree
    val core = new Node(new VisLabel("Core"))
    core.setExpanded(true)
    val dummy = new Node(new VisLabel("Dummy"))

    addSample(dummy, "Dummy Circle")
    addSample(dummy, "Dummy Circle 2")
    addSample(core, "Entities Playground")
    addSample(core, "Box2d World")

    tree.add(core)
    tree.add(dummy)

    val simulationLabel = new VisLabel("Simulation")
    simulationLabel.setColor(Color.LIGHT_GRAY)
    eastPane.add(simulationLabel).fillX().expandX().row()

    val pauseCheckbox = new VisCheckBox("update")
    pauseCheckbox.addListener(new ChangeListener {
      override def changed(event: ChangeListener.ChangeEvent, actor: Actor): Unit = {
        pause = !pauseCheckbox.isChecked
      }
    })

    eastPane.add(pauseCheckbox).row()

    val samplesLabel = new VisLabel("Samples")
    samplesLabel.setColor(Color.LIGHT_GRAY)

    eastPane.add(samplesLabel).fillX().expandX().row()
    eastPane.add(tree).expand().fill().row()
    eastPane.setMovable(false)

    loadingLog = new VisLabel("")
    loadingLog.setColor(blue)
    eastPane.add(loadingLog).fillX().expandX().row()

    loadingBar = new VisProgressBar(0f, 100f, 1f, false)
    loadingBar.setAnimateDuration(2)
    eastPane.add(loadingBar).fillX().expandX()

    samplePane = new VisWindow("")
    samplePane.setWidth(defaultPaneSize)

    menuBar = new MenuBar()
    createMenu()

    val root = new VisTable()
    root.setFillParent(true)
    root.add(menuBar.getTable).fillX().expandX().row()
    root.add(samplePane).width(currentSampleMenuWidth).fillY().expandY()
    viewport = root.add()
    resizeGui()
    root.add(eastPane).width(defaultPaneSize).fillY().expandY()
    gui.addActor(root)
  }

  private def centerToast(toast: Toast) = {
    toast.getMainTable.setY(
      gui.getHeight - toast.getMainTable.getPrefHeight - toastManager.getScreenPadding)
    toast.getMainTable.setX((gui.getWidth / 2) - toast.getMainTable.getPrefWidth / 2 )
  }

  private def matchSample(sampleName: String): Option[Sample] = sampleName match {
    case "Dummy Circle" => Option(DummyCircleSample())
    case "Entities Playground" => Option(new ScalaEntitySystemModule())
    case "Box2d World" => Option(new ScalaBox2dModule())
    case _ => None
  }

  private def resetLoadingBar(): Unit = {
    loadingBar.setAnimateDuration(0)
    loadingBar.setValue(0)
    loadingBar.setAnimateDuration(0.2f)
  }

  private def resetInputProcessor(sample: Sample): Unit = {
    inputMultiplexer.clear()
    sample.attachInputProcessors(inputMultiplexer)
    inputMultiplexer.addProcessor(this)
    inputMultiplexer.addProcessor(gui)
    Gdx.input.setInputProcessor(inputMultiplexer)
  }

  private def buildSampleShortcutTable(sample: Sample): Unit = {
    currSampleKeybindings = None
    sample.getKeyShortcuts foreach(kb => {
      val shortcuts = new KeyHelpTable(true)
      kb.foreach(k => {
        if (!k._1.contains("+")) shortcuts.addKeyBinding(k._1, k._2)
        else shortcuts.addKeyBinding(k._1.split("\\+"), k._2)
      })
      currSampleKeybindings = Option(shortcuts)
    })
  }

  private def resetSamplePane() = {
    samplePane.clear()
    samplePane.setKeepWithinParent(false)
    samplePane.setKeepWithinStage(false)
  }

  private def displayModuleLoadedToast() = {
    val toast = new Toast("dark", new VisTable(true))
    toast.getContentTable.add(new VisLabel("Loaded module: "))
    val t = new VisLabel(currSampleName + "!")
    t.setColor(Color.CORAL)
    toast.getContentTable.add(t)
    toastManager.show(toast, 5)
    centerToast(toast)
  }

  private def setSample(sample: Sample): Unit = {
    if (loadingFinished) {
      loadingFinished = false

      resetLoadingBar()
      resetSamplePane()
      sample.init(this)
      loadingBar.setValue(5)

      sample.initGui(samplePane)
      loadingBar.setValue(20)

      resetInputProcessor(sample)
      loadingBar.setValue(50)

      buildSampleShortcutTable(sample)
      loadingBar.setValue(80)

      currentSample.foreach(old => old.cleanup())
      currentSample = Some(sample)
      nextSample = None

      new Thread(new Runnable {
        override def run(): Unit = {
          sample.setup((msg: String) => loadingLog.setText(msg))
          loadingFinished = true
          loadingBar.setValue(100)
          displayModuleLoadedToast()
          loadingLog.setText("")
        }
      }).start()
    }
  }

  override def create(): Unit = {
    super.create()
    gui = new Stage(new ScreenViewport())
    initGui()

    inputMultiplexer = new InputMultiplexer()
    inputMultiplexer.addProcessor(this)
    inputMultiplexer.addProcessor(gui)

    currentSample.foreach(s => {
      s.init(this)
      s.attachInputProcessors(inputMultiplexer)
    })

    Gdx.input.setInputProcessor(inputMultiplexer)
  }

  override def doRender(): Unit = {
    if (loadingFinished) currentSample.foreach(s => s.render(shapeRenderer))
    gui.draw()
  }

  override def doUpdate(delta: Float): Unit = {
    gui.act(delta)
    nextSample.foreach(s => setSample(s))
    if (loadingFinished) currentSample.foreach(s => s.update(delta))
  }

  override def resize(newWidth: Int, newHeight: Int): Unit = {
    super.resize(newWidth, newHeight)
    currentSample.foreach(s => s.resize(newWidth, newHeight))
    gui.getViewport.update(newWidth, newHeight, true)
    resizeGui()
  }

  override def dispose(): Unit = {
    super.dispose()
    VisUI.dispose()
  }

  override def keyUp(keycode: Int): Boolean = {
    super.keyUp(keycode)
    if (keycode == Keys.H) {
      samplePane.addAction(
        Actions.moveTo(if (lastTransitionOut) -samplePane.getWidth else 0,
          0, 0.30f))
      lastTransitionOut = !lastTransitionOut
    }
    false
  }
}