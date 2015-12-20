/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package trabalho.de.pd.servidor;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ASUS
 */
public class RecebePedidoCliente extends Thread {
    
    Pedido pedido;
    Servidor servidor = null;
    Socket socket = null;
    Boolean running = null;
    
    public RecebePedidoCliente(Servidor servidor,Socket socket) {
        this.servidor = servidor;
        this.socket = socket;
        running = true;
    }
    
    public void termina() {
        running = false;
    }
    
    @Override
    public void run() {
        System.out.println("[SERVIDOR] Thread RecebePedidoCliente - " + socket.getInetAddress().getHostAddress() + ":"
                + socket.getLocalPort());
        servidor.enviaListaFicheiros(socket);
        do {
            try {
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                pedido = (Pedido) ois.readObject();
                switch (pedido.getTipoPedido()) {
                    case Pedido.DOWNLOAD:
                            servidor.arrancaThreadEnviaFicheiro(socket, pedido);
                        break;
                    case Pedido.UPLOAD:
                            servidor.arrancaThreadRecebeFicheiro(socket,pedido);
                        break;           
                    case Pedido.ELIMINAR:
                            servidor.arrancaThreadEliminaFicheiro(pedido);
                        break;
                    default:
                        break;
                }
            } catch (IOException ex) {
                System.out.println("RecebePedido Timeout");
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(RecebePedidoCliente.class.getName()).log(Level.SEVERE, null, ex);
            }
        } while (running);
    }
}