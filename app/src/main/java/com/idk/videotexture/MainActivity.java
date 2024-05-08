package com.idk.videotexture;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaDataSource;
import android.media.MediaPlayer;
import static android.opengl.GLES30.*;

import android.net.Uri;
import android.opengl.GLES11Ext;
import android.opengl.GLES31Ext;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;


import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.channels.Channels;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity implements GLSurfaceView.Renderer {


    public MediaPlayer player;
    private SurfaceTexture surfaceTexture;
    private int programID;

    private int positionBuffer;
    private int uvBuffer;
    private int indiceBuffer;

    private int positionLocation;
    private int uvLocation;
    private int textureLocation;
    private int textureID;

    //
    float[] positionArray = {-1, -1, 1, -1, 1, 1, 1, 1, 1,  1, -1, 1};
    float[] uvArray = {0,0, 0,1, 1,1, 1,0};
    int[] indexArray = {0,2,3,0,1,2};
    boolean hasError;

    @Override
    protected void onCreate( Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        GLSurfaceView view = new GLSurfaceView(getApplicationContext());
        view.setEGLContextClientVersion(3);
        view.setEGLConfigChooser(8, 8, 8, 8, 24, 8);
        view.setPreserveEGLContextOnPause(true);
        view.setRenderer(this);
        setContentView(view);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            player = new MediaPlayer();
            AssetFileDescriptor des = getAssets().openFd("example.mp4");
            player.setDataSource(des.getFileDescriptor(),des.getStartOffset(),des.getLength());
            player.prepare();
            int[] texId = new int[1];
            glGenTextures(1,texId,0);
            textureID = texId[0];
            surfaceTexture = new SurfaceTexture(textureID);
            player.setSurface(new Surface(surfaceTexture));

            //create/initiate shader
            programID = glCreateProgram();
            int vertexShader = glCreateShader(GL_VERTEX_SHADER);
            int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(vertexShader,"""
                    attribute vec4 position;
                            attribute vec2 uv;
                            varying vec2 coord;
                            void main(){
                            gl_Position = position;
                            coord = vec2(uv.x ,1.0 - uv.y);
                        }
                    """ );
            glShaderSource(fragmentShader,
                    """
                     #extension GL_OES_EGL_image_external : require
                            precision highp float;
                            uniform samplerExternalOES texture0;
                            varying vec2 coord;
                            void main(){
                            gl_FragColor =  texture2D(texture0,coord);
                            }
                    """);
            glCompileShader(vertexShader);
            glCompileShader(fragmentShader);
            int[] result =new int[1];
            glGetShaderiv(vertexShader,GL_COMPILE_STATUS,result,0);
            if(result[0] != GL_TRUE){
                hasError = true;
                System.out.println(glGetShaderInfoLog(vertexShader));
            }
            glGetShaderiv(fragmentShader,GL_COMPILE_STATUS,result,0);
            if(result[0] != GL_TRUE){
                hasError = true;
                System.out.println(glGetShaderInfoLog(fragmentShader));
            }
            glAttachShader(programID,vertexShader);
            glAttachShader(programID,fragmentShader);
            glLinkProgram(programID);

            positionLocation = glGetAttribLocation(programID,"position");
            uvLocation = glGetAttribLocation(programID,"uv");
            textureLocation = glGetUniformLocation(programID,"texture0");

            int[] tmp = new int[3];
            glGenBuffers(3,tmp,0);

            positionBuffer = tmp[0];
            uvBuffer = tmp[1];
            indiceBuffer = tmp[2];


            //quad vertex into Native Buffer
            FloatBuffer pBuffer = createFloatBuffer(positionArray.length);
            pBuffer.put(positionArray).position(0);

            FloatBuffer uBuffer = createFloatBuffer(uvArray.length);
            uBuffer.put(uvArray).position(0);

            IntBuffer iBuffer = createIntBuffer(indexArray.length);
            iBuffer.put(indexArray).position(0);

            // quad vertex native buffer into opengl buffer
            glBindBuffer(GL_ARRAY_BUFFER,positionBuffer);
            glBufferData(GL_ARRAY_BUFFER,positionArray.length*4,pBuffer,GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER,uvBuffer);
            glBufferData(GL_ARRAY_BUFFER,uvArray.length*4,uBuffer,GL_STATIC_DRAW);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indiceBuffer);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,indexArray.length*4,iBuffer,GL_STATIC_DRAW);

            glBindBuffer(GL_ARRAY_BUFFER,0);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0);

            player.start();

        }catch (Exception e){
            hasError = true;
            e.printStackTrace();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {

    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if(hasError){
            return;
        }

        glClearColor(0,0,0,1);
        glClear(GL_COLOR_BUFFER_BIT);
        if(!player.isPlaying()){
            return;
        }
        surfaceTexture.updateTexImage();
        glUseProgram(programID);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureID);
        glUniform1i(textureLocation,0);

        glBindBuffer(GL_ARRAY_BUFFER,positionBuffer);
        glVertexAttribPointer(positionLocation,3,GL_FLOAT,false,3 * 4,0);
        glEnableVertexAttribArray(positionLocation);

        glBindBuffer(GL_ARRAY_BUFFER,uvBuffer);
        glVertexAttribPointer(uvLocation,2,GL_FLOAT,false,2 * 4,0);
        glEnableVertexAttribArray(uvLocation);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indiceBuffer);
        glDrawElements(GL_TRIANGLES,indexArray.length,GL_UNSIGNED_INT,0);

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0);
        glBindBuffer(GL_ARRAY_BUFFER,0);

        glUseProgram(0);
    }

    static FloatBuffer createFloatBuffer(int i){
        ByteBuffer buffer = ByteBuffer.allocateDirect(i * 4);
        buffer.order(ByteOrder.nativeOrder());
        FloatBuffer fb = buffer.asFloatBuffer();
        fb.position(0);
        return fb;
    }
    static IntBuffer createIntBuffer(int i){
        ByteBuffer buffer = ByteBuffer.allocateDirect(i * 4);
        buffer.order(ByteOrder.nativeOrder());
        IntBuffer ib = buffer.asIntBuffer();
        ib.position(0);
        return ib;
    }

}
