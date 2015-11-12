/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package trabalho.de.pd.servidor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ASUS
 */
public class Servidor implements Serializable{
    public static final int MAX_SIZE=256;
    public String diretoria=null;
    public InetAddress group;
    public int port;
    public int contador=0;
    public byte []file=null;
    public FileInputStream recvFile=null;
    public FileOutputStream sendFile=null;
    public ObjectOutputStream sendobject=null;
    public ObjectInputStream reciveobject=null;
    
    //threads
    public Runnable HeartbeatsEnvia=null;
    public Runnable HeartbeatsRecebe=null;
    public Runnable TrataTCP=null;
    
    //UDP
    protected MulticastSocket socketUDP=null;
    private DatagramPacket packetUDP=null;
    
    //TCP
    private ServerSocket socketTCP=null;
    private Socket SocketComPrimario=null;
    private Socket SocketComSecundario=null;
    
    private boolean debug,Primario,Linked,Actualizado;
    
    public Servidor(String diretoria,int Porto) throws UnknownHostException, IOException, InterruptedException 
    {       
        group=InetAddress.getByName("225.15.15.15");
        packetUDP=new DatagramPacket(new byte[MAX_SIZE],MAX_SIZE);
        file=new byte[MAX_SIZE];
        this.diretoria=diretoria;
        port=Porto;
        socketUDP = new MulticastSocket(port);
        socketUDP.joinGroup(group);
        Primario=false;
        Linked=false;
        Actualizado=false;
        debug=true;
        
        try{
            constroiThreads();
            começa();
        }catch(NumberFormatException e){
            System.out.println("O porto de escuta deve ser um inteiro positivo.");
        }catch(SocketException e){
            System.out.println("Ocorreu um erro ao nível do socket UDP:\n\t"+e);
        }catch(IOException e){
            System.out.println("Ocorreu um erro no acesso ao socket:\n\t"+e);
        }catch(InterruptedException e){
            System.out.println("Ocorreu um erro no sleep");
        }finally{
                closeSocket();
        }
    }
    
    public void constroiThreads() throws IOException,InterruptedException
    {
        //Socket toClientSocket;
        HeartbeatsEnvia = new Runnable() {
            @Override
            public void run() {
                do {
                    try {
                        if(Linked==true || Primario==true)
                        {
                            ByteArrayOutputStream byteout = new ByteArrayOutputStream(MAX_SIZE);
                            ObjectOutputStream send = new ObjectOutputStream(byteout);
                            send.writeObject(Primario);
                            packetUDP.setData(byteout.toByteArray());
                            packetUDP.setLength(byteout.size());
                            socketUDP.send(packetUDP);
                            Thread.sleep(5000);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("O porto de escuta deve ser um inteiro positivo.");
                    } catch (SocketException e) {
                        System.out.println("Ocorreu um erro ao nível do socket UDP:\n\t" + e);
                    } catch (IOException e) {
                        System.out.println("Ocorreu um erro no acesso ao socket:\n\t" + e);
                    } catch (InterruptedException e) {
                        System.out.println("Ocorreu um erro no sleep");
                    } finally {
                        
                    }                   
                } while (debug);
            }
        };
        
        HeartbeatsRecebe = new Runnable(){

            @Override
            public void run() {
                do{                  
                    try {
                        if (Linked == false && Primario == false) {
                            if (contador == 3) {
                                Primario = true;
                            } else {
                                ObjectInputStream recv = null;
                                socketUDP.setSoTimeout(5000);
                                socketUDP.receive(packetUDP);
                                recv = new ObjectInputStream(new ByteArrayInputStream(packetUDP.getData()));
                                Object msg = recv.readObject();
                                if (msg instanceof Boolean) {
                                    if (true == (Boolean) msg && Primario==false) {
                                        Linked = true;
                                        socketTCP = new ServerSocket(socketUDP.getPort(), 0, socketUDP.getInetAddress());
                                        //receber todos os dados do servidor primario
                                        SocketComPrimario=socketTCP.accept();
                                        OutputStream oss=SocketComPrimario.getOutputStream();
                                        sendobject=new ObjectOutputStream(oss);
                                        sendobject.writeBoolean(Actualizado);
                                        sendobject.close();
                                    }
                                }
                            }
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("O porto de escuta deve ser um inteiro positivo.");
                    } catch (SocketException e) {
                        System.out.println("Ocorreu um erro ao nível do socket UDP:\n\t" + e);
                    } catch (IOException e) {
                        System.out.println("Ocorreu um erro no acesso ao socket:\n\t" + e);
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        if(Linked==false && Primario==false)
                            contador++;
                        else{
                            contador=0;
                        }
                    }
                }while(Linked);
            }         
        };
        
        TrataTCP = new Runnable() {

            @Override
            public void run() {
                do {
                    try {
                        if (Linked == true) {
                            if (Primario == false && Actualizado == false) {
                                int tamanho;
                                byte[] bytes = new byte[MAX_SIZE];
                                InputStream in = null;
                                OutputStream ou = null;
                                in = SocketComSecundario.getInputStream();
                                ou = new FileOutputStream(diretoria);
                                while ((tamanho = in.read(bytes)) > 0) {
                                    ou.write(bytes, 0, tamanho);
                                }
                                in.close();
                                in.close();
                                Actualizado = true;
                            } else {
                                if (Primario == true) {
                                    SocketComSecundario = socketTCP.accept();
                                    InputStream iss = SocketComSecundario.getInputStream();
                                    reciveobject = new ObjectInputStream(iss);
                                    Object msgrecebida = reciveobject.readObject();
                                    if (msgrecebida instanceof Boolean) {
                                        if ((Boolean) msgrecebida == false) {
                                            File folder = new File(diretoria);
                                            File[] ListadosFicheiros = folder.listFiles();
                                            OutputStream ou = SocketComSecundario.getOutputStream();
                                            InputStream in = null;
                                            for (int i = 0; i < ListadosFicheiros.length; i++) {
                                                long length = ListadosFicheiros[i].length();
                                                byte[] bytes = new byte[MAX_SIZE];
                                                in = new FileInputStream(ListadosFicheiros[i]);
                                                int tamanho;
                                                while ((tamanho = in.read(bytes)) > 0) {
                                                    ou.write(bytes, 0, tamanho);
                                                }
                                            }
                                            ou.close();
                                            in.close();
                                        }
                                    }else{
                                        if(msgrecebida instanceof Cliente)
                                        {
                                            
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(Servidor.class.getName()).log(Level.SEVERE, null, ex);
                    }

                } while (debug);
            }

        };
        
    }
    
    public void começa() throws IOException,InterruptedException                      //nao sei usar o daemon, e nao sei como se fazia para istoo acabar so quando as threads acabarem
    {     
        HeartbeatsEnvia.run();
        HeartbeatsRecebe.run();
        TrataTCP.run();
    }
    
    public void closeSocket() throws IOException
    {
        if(socketUDP!=null)
            socketUDP.close();
        if(socketTCP!=null)
            socketTCP.close();
    }
}
