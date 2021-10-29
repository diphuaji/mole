import MoleConfig.TunnelConfig
import com.jcraft.jsch._

import java.awt.{BorderLayout, Component}
import java.io.{IOException, File}
import javax.swing._
import javax.swing.table.{DefaultTableCellRenderer, DefaultTableModel, TableCellEditor, TableCellRenderer}
import scala.jdk.CollectionConverters._
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import java.awt.Color
import java.lang.Thread.{UncaughtExceptionHandler, sleep}
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}


class SshUserInfo(val username: String, val host: String, val port: Int = 22, val password: String) extends UserInfo {

  val pathToPrivateKey = {
    val sep = File.separator
    f"${System.getProperty("user.home")}$sep.ssh${sep}id_rsa"
  }

  def getPassphrase: String = ""

  def getPassword: String = ""

  def promptPassword(message: String): Boolean = true

  def promptPassphrase(message: String): Boolean = true

  def promptYesNo(message: String): Boolean = true

  def showMessage(message: String): Unit = {}

}


class TunnelThread(val jsch: JSch, val sshUserInfo: SshUserInfo, val tunnelConfig: TunnelConfig, val digButton: DigButton, val table: JTable) extends Thread {
  private val timeoutSeconds = 3
  private var _shouldStop = false

  def setShouldStop(value: Boolean) = this.synchronized {
    _shouldStop = value
    //    println(f"set: $value")
  }

  override def run(): Unit = {
    println("private key: " + sshUserInfo.pathToPrivateKey)
    jsch.addIdentity(sshUserInfo.pathToPrivateKey)
    println("private key set")
    try {
      val session = jsch.getSession(sshUserInfo.username, sshUserInfo.host, sshUserInfo.port)
      println("session created")
      session.setUserInfo(sshUserInfo)
      println(f"Assigning port: {${tunnelConfig.lport}}")
      val assinged_port = session.setPortForwardingL(tunnelConfig.lport, tunnelConfig.rhost, tunnelConfig.rport)

      println(f"Assigned port: {$assinged_port}")

      session.connect(timeoutSeconds * 1000)
      digButton.connectionStatus = ConnectionStatus.CONNECTED
      digButton.setText("Disconnect")
      digButton.setEnabled(true)
      table.repaint()

      while (!_shouldStop) {
        //      println("don't stop!")
        sleep(1000)
      }

      session.disconnect()
      digButton.connectionStatus = ConnectionStatus.NOT_CONNECTED
    } catch {
      case e =>
        println(f"soemthing wrong:${e.getMessage}")
        JOptionPane.showMessageDialog(digButton, f"Connection error:\n${e.getMessage}")
        digButton.connectionStatus = ConnectionStatus.NOT_CONNECTED
    } finally {
      println(f"finally block")
      digButton.setEnabled(true)
      digButton.setText("Connect")
      table.repaint()
    }
  }
}

class DigButton(val tunnelConfig: TunnelConfig) extends JButton("Connect") {
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

class MoleConfig(val config: Config) {
  private val moleConfig = config.getConfig("com.diphuaji.mole")
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

object MoleConfig {
  case class TunnelConfig(name: String, sshHost: String, lport: Int, rhost: String, rport: Int)

  def apply(file: File) = new MoleConfig(ConfigFactory.parseFile(file))
}

object Main extends App {
  //  MoleConfig.printConfig
  //  System.exit(0)
  val jsch = new JSch
  val STATUS_COL = 4
  val ACTION_COL = 5

  //Create and set up the window.
  val frame: JFrame = new JFrame("Mole")
  //  frame.setLayout()
  frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

  val tableModel = new DefaultTableModel()
  val table = new JTable(tableModel)

  def setupUI(tunnels: Seq[TunnelConfig]) = {

    tableModel.addColumn("lport")
    tableModel.addColumn("rhost")
    tableModel.addColumn("rport")
    tableModel.addColumn("server")
    tableModel.addColumn("status")
    tableModel.addColumn("action")

    tunnels.foreach(tunnel =>
      tableModel.addRow(Seq(tunnel.lport, tunnel.rhost, tunnel.rport, tunnel.sshHost).asJava.toArray())
    )
    val actionButtons = tunnels.map { tunnel =>
      val button = new DigButton(tunnel)
      button.addActionListener(new ActionListener {
        def actionPerformed(event: ActionEvent) = {
          val sshUserInfo = new SshUserInfo(username = System.getProperty("user.name"), host = tunnel.sshHost, password = "314152380")
          button.connectionStatus match {
            case ConnectionStatus.NOT_CONNECTED =>
              val thread = new TunnelThread(jsch, sshUserInfo, tunnel, button, table)
              button.tunnelThread = Some(thread)
              thread.setUncaughtExceptionHandler(new UncaughtExceptionHandler {
                def uncaughtException(var1: Thread, var2: Throwable) = {
                  //                  println("something wrong!")
                  button.setText("Connect")
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
    }

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
  }

  val footer: JLabel = new JLabel("From Diphuaji with Love")
  val scrollPane = new JScrollPane(table)
  table.setFillsViewportHeight(true)
  val contentPane = frame.getContentPane

  val fc = new JFileChooser()
  val openFileButton = new JButton("Open Config")
  openFileButton.addActionListener(new ActionListener {
    override def actionPerformed(actionEvent: ActionEvent): Unit = {
      try {
        val returnVal = fc.showOpenDialog(openFileButton)
        if (returnVal == JFileChooser.APPROVE_OPTION) {
          val file = fc.getSelectedFile
          val moleConfig = MoleConfig.apply(file)
          setupUI(moleConfig.tunnels)
          openFileButton.setEnabled(false)
        }
      } catch {
        case e: Throwable =>
          JOptionPane.showMessageDialog(openFileButton, f"The config file is invalid:\n${e.getMessage}")
      }
    }
  })
  contentPane.add(scrollPane, BorderLayout.CENTER)
  contentPane.add(openFileButton, BorderLayout.PAGE_START)
  contentPane.add(footer, BorderLayout.PAGE_END)

  scrollPane.add(new JLabel("xxx"))

  //Display the window.
  frame.pack()
  frame.setVisible(true)

}
