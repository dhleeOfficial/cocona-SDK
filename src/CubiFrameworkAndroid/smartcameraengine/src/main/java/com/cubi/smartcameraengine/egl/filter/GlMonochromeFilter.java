package com.cubi.smartcameraengine.egl.filter;

import android.opengl.GLES20;

/**
 * Created by sudamasayuki on 2017/05/18.
 */

public class GlMonochromeFilter extends GlFilter {

    private static final String FRAGMENT_SHADER =
            "precision lowp float;" +

                    "varying highp vec2 vTextureCoord;" +
                    "uniform lowp sampler2D sTexture;" +
                    "uniform float intensity;" +
                    "uniform vec3 filterColor;" +

                    "const mediump vec3 luminanceWeighting = vec3(0.2125, 0.7154, 0.0721);" +

                    "void main() {" +

                    "lowp vec4 textureColor = texture2D(sTexture, vTextureCoord);" +
                    "float luminance = dot(textureColor.rgb, luminanceWeighting);" +

                    "lowp vec4 desat = vec4(vec3(luminance), 1.0);" +

                    "lowp vec4 outputColor = vec4(" +
                    "(desat.r < 0.5 ? (2.0 * desat.r * filterColor.r) : (1.0 - 2.0 * (1.0 - desat.r) * (1.0 - filterColor.r)))," +
                    "(desat.g < 0.5 ? (2.0 * desat.g * filterColor.g) : (1.0 - 2.0 * (1.0 - desat.g) * (1.0 - filterColor.g)))," +
                    "(desat.b < 0.5 ? (2.0 * desat.b * filterColor.b) : (1.0 - 2.0 * (1.0 - desat.b) * (1.0 - filterColor.b)))," +
                    "1.0" +
                    ");" +

                    "gl_FragColor = vec4(mix(textureColor.rgb, outputColor.rgb, intensity), textureColor.a);" +
                    "}";

    private float intensity = 1.0f;
    private float[] filterColor = new float[]{0.6f, 0.45f, 0.3f};

    public GlMonochromeFilter() {
        super(DEFAULT_VERTEX_SHADER, FRAGMENT_SHADER);
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    @Override
    public void onDraw() {
        GLES20.glUniform1f(getHandle("intensity"), intensity);
        GLES20.glUniform3fv(getHandle("filterColor"), 0, filterColor, 0);
    }

}
