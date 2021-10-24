import ConnectionStatus.ConnectionStatusVal
import com.jcraft.jsch._

import java.awt.{BorderLayout, Component}
import java.util.{EventObject, List, Properties}
import java.io.IOException
import java.util
import javax.swing._
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel, TableCellEditor, TableCellRenderer}
import scala.jdk.CollectionConverters._
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import javax.swing.event.{CellEditorListener, MouseInputListener}
import java.awt.Color
import javax.swing.BorderFactory
import java.awt.Color
import java.lang.Thread.{UncaughtExceptionHandler, sleep}
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}

class SshUserInfo(val username: String, val host: String, val port: Int = 22, val password: String) extends UserInfo {
  def getPassphrase: String = ""

  def getPassword: String = "314152380"

  def promptPassword(message: String): Boolean = true

  def promptPassphrase(message: String): Boolean = true

  def promptYesNo(message: String): Boolean = true

  def showMessage(message: String): Unit = {}
}

case class TunnelConfig(lport: Int, rhost: String, rport: Int, timeout_seconds: Int = 1)

class TunnelThread(val jsch: JSch, val sshUserInfo: SshUserInfo, val tunnelConfig: TunnelConfig, val digButton: DigButton, val table: JTable) extends Thread {
  private var _shouldStop = false

  def setShouldStop(value: Boolean) = this.synchronized {
    _shouldStop = value
    //    println(f"set: $value")
  }

  override def run(): Unit = {
    val session = jsch.getSession(sshUserInfo.username, sshUserInfo.host, sshUserInfo.port)
    session.setUserInfo(sshUserInfo)
    val assinged_port = session.setPortForwardingL(tunnelConfig.lport, tunnelConfig.rhost, tunnelConfig.rport)
    println(f"Assigning port: {$assinged_port}")
    try {
      session.connect(tunnelConfig.timeout_seconds * 1000)
      digButton.connectionStatus = ConnectionStatus.CONNECTED
      digButton.setText("Disconnect")
    } catch {
      case e =>
        digButton.connectionStatus = ConnectionStatus.NOT_CONNECTED
        digButton.setText("Connect")
    }finally {
      digButton.setEnabled(true)
      table.repaint()
    }
    println(f"Assigned port: {$assinged_port}")
    while (!_shouldStop) {
      //      println("don't stop!")
      sleep(1000)
    }

    session.disconnect()
    digButton.connectionStatus = ConnectionStatus.NOT_CONNECTED
    digButton.setText("Connect")
    digButton.setEnabled(true)
    table.repaint()
  }
}

class DigTunnel extends ActionListener {
  def actionPerformed(event: ActionEvent): Unit = {

  }
}

class DigButton(val lport: Int, val rhost: String, val rport: Int) extends JButton("CONNECT") {
  private var _connectionStatus = ConnectionStatus.NOT_CONNECTED
  def connectionStatus = _connectionStatus
  def connectionStatus_=(value: ConnectionStatus.ConnectionStatusVal) = this.synchronized {
    _connectionStatus = value
  }

  var tunnelThread: Option[TunnelThread] = None
}

object ConnectionStatus extends Enumeration {
  case class ConnectionStatusVal(statusString: String, color: Option[Color]) extends super.Val

  import scala.language.implicitConversions

  implicit def valueToConnectionStatusVal(x: Value): ConnectionStatusVal = x.asInstanceOf[ConnectionStatusVal]

  val NOT_CONNECTED = ConnectionStatusVal("Not Connected", None)
  val CONNECTED = ConnectionStatusVal("Connected", Some(Color.GREEN))
  val CONNECTION_FAILED = ConnectionStatusVal("Connection Failed", Some(Color.RED))
  val CONNECTING = ConnectionStatusVal("Connecting", Some(Color.YELLOW))
  val DISCONNECTING = ConnectionStatusVal("Disconnecting", Some(Color.YELLOW))
}

object MoleConfig {
  case class TunnelConfig(name: String, sshHost: String, lport: Int, rhost: String, rport: Int)

  private val moleConfig = ConfigFactory.load().getConfig("com.diphuaji.mole")
  val tunnels: Seq[TunnelConfig] = moleConfig.getConfigList("tunnels").asScala.toSeq.map { tunnel =>
    TunnelConfig(
      tunnel.getString("name"),
      tunnel.getString("ssh_host"),
      tunnel.getInt("lport"),
      tunnel.getString("rhost"),
      tunnel.getInt("rport"),
    )
  }

  def printConfig: Unit = println(moleConfig.root().render(ConfigRenderOptions.defaults().setOriginComments(false)))
}

object Main extends App {
  //  MoleConfig.printConfig
  //  System.exit(0)
  val jsch = new JSch
  val STATUS_COL = 4
  val ACTION_COL = 5

  //Create and set up the window.
  val frame: JFrame = new JFrame("HelloWorldSwing")
  //  frame.setLayout()
  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)


  val tableModel = new DefaultTableModel()
  tableModel.addColumn("lport")
  tableModel.addColumn("rhost")
  tableModel.addColumn("rport")
  tableModel.addColumn("server")
  tableModel.addColumn("status")
  tableModel.addColumn("action")

  val rows = MoleConfig.tunnels.map { tunnel =>
    Seq(tunnel.lport, tunnel.rhost, tunnel.rport, tunnel.sshHost)
  }

  val actionButtons = rows.zipWithIndex.map({
    case (row, index) =>
      val button = new DigButton(row.head.asInstanceOf[Int], row(1).asInstanceOf[String], row(2).asInstanceOf[Int])
      button.addActionListener(new ActionListener {
        def actionPerformed(event: ActionEvent) = {
          val sshUserInfo = new SshUserInfo(username = "billy", host = "localhost", password = "314152380")
          val tunnelConfig = TunnelConfig(button.lport, button.rhost, button.rport)

          button.connectionStatus match {
            case ConnectionStatus.NOT_CONNECTED =>
              val thread = new TunnelThread(jsch, sshUserInfo, tunnelConfig, button, table)
              button.tunnelThread = Some(thread)
              thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
                def uncaughtException(var1: Thread, var2: Throwable) = {
                  //                  println("something wrong!")
                  button.setText("CONNECT")
                }
              })
              thread.start()
              button.connectionStatus = ConnectionStatus.CONNECTING
              button.setEnabled(false)
              button.setText("CONNECTING")
            case ConnectionStatus.CONNECTED =>
              button.tunnelThread.foreach(thread => thread.setShouldStop(true))
              button.setEnabled(false)
              button.setText("DISCONNECTING")
              button.connectionStatus = ConnectionStatus.DISCONNECTING
          }
          table.repaint()
        }
      })
      button
  })
  rows.foreach(row => tableModel.addRow(row.asJava.toArray()))

  val table = new JTable(tableModel)

  val actionColumn = table.getColumnModel.getColumn(ACTION_COL)
  actionColumn.setPreferredWidth(100)
  actionColumn.setCellRenderer(new DefaultTableCellRenderer {
    override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) = {
      actionButtons(row)
    }
  })

  actionColumn.setCellEditor(new AbstractCellEditor with TableCellEditor {
    def getTableCellEditorComponent(table: JTable, value: Any, isSelected: Boolean, row: Int, column: Int) = {
      actionButtons(row)
    }

    def getCellEditorValue = {
      1
    }
  })

  val statusColumn = table.getColumnModel.getColumn(STATUS_COL)
  statusColumn.setCellRenderer(new DefaultTableCellRenderer {
    override def getTableCellRendererComponent(table: JTable, value: Any, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int) = {
      actionButtons(row).connectionStatus.color.map(color => {
        //        println(color)
        val l = new JLabel()
        l.setOpaque(true)
        l.setBackground(color)
        l.setForeground(Color.cyan)
        l
      }).getOrElse(new JLabel())
    }
  })

  //Add the ubiquitous "Hello World" label.
  val label: JLabel = new JLabel("Hello World")
  val footer: JButton = new JButton("Binggo")
  footer.getPressedIcon
  val scrollPane = new JScrollPane(table)
  table.setFillsViewportHeight(true)
  val contentPane = frame.getContentPane
  contentPane.add(scrollPane, BorderLayout.CENTER)
  contentPane.add(label, BorderLayout.PAGE_START)
  contentPane.add(footer, BorderLayout.PAGE_END)

  scrollPane.add(new JLabel("xxx"))

  //Display the window.
  frame.pack()
  frame.setVisible(true)

}
