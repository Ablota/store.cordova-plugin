package com.ablota.store.plugin;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class OpenGLActivity extends Activity implements GLSurfaceView.Renderer {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final GLSurfaceView view;
		view = new GLSurfaceView(this);
		view.setEGLContextClientVersion(2);
		view.setRenderer(this);

		setContentView(view);
	}

	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Intent data = new Intent();
		data.putExtras(getIntent());
		data.putExtra("renderer", GLES20.glGetString(GLES20.GL_RENDERER));

		setResult(Activity.RESULT_OK, data);
		finish();
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
	}

	@Override
	public void onDrawFrame(GL10 gl) {
	}
}
