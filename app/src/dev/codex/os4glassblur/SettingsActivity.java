package dev.codex.os4glassblur;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Settings page for OS4 Glass Mode: tune every parameter live.
 *
 * Values are persisted into Settings.System (key os4_glass_blur_config) as
 * JSON using the WRITE_SETTINGS special permission (no root needed). The
 * module inside SystemUI reads them through a ContentObserver, so most
 * changes apply immediately; "Save & restart" additionally restarts
 * SystemUI (via root, when available) so already-rendered rows pick up
 * the new values.
 */
public class SettingsActivity extends Activity {

    private EditText radiusEdit;
    private EditText panelEdit;
    private EditText brightnessEdit;
    private EditText darkerEdit;
    private EditText iorEdit;
    private EditText burnEdit;
    private EditText saturationEdit;
    private EditText alphaEdit;
    private EditText tintREdit;
    private EditText tintGEdit;
    private EditText tintBEdit;
    private EditText edgeEdit;
    private EditText reflectEdit;
    private EditText lightEdit;
    private EditText bgSatEdit;
    private EditText bgBrightEdit;
    private EditText panelScaleEdit;
    private Switch rowGlassSwitch;
    private Switch sdfClampSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadCurrent();
        if (!Settings.System.canWrite(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("需要\"修改系统设置\"权限")
                    .setMessage("参数保存在系统设置中，请授予本模块\"修改系统设置\"权限（无需 root）。")
                    .setPositiveButton("去授权", (d, w) -> startActivity(new Intent(
                            Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    private void buildUi() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("OS4 玻璃模式统一低模糊 · 参数设置");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("保存后自动生效（无需重启，行渲染会在下次布局时刷新）。");
        hint.setTextSize(12);
        hint.setPadding(0, 0, 0, dp(16));
        root.addView(hint);

        radiusEdit = addNumberRow(root, "玻璃模糊半径（默认 40）", "统一各层玻璃模糊半径，0~500");
        panelEdit = addNumberRow(root, "面板背景模糊比例（默认 50）", "整屏背景模糊百分比，0~100");
        brightnessEdit = addNumberRow(root, "亮度微调（默认 -0.05）", "玻璃卡片亮度增量，如 -0.5 / 0.2");
        darkerEdit = addNumberRow(root, "压暗微调（默认 -0.08）", "玻璃压暗增量，如 -0.3 / 0.1");
        iorEdit = addNumberRow(root, "折射微调（默认 0.20）", "折射 IOR 增量（对渲染影响有限）");
        burnEdit = addNumberRow(root, "烧焦微调（默认 -0.25）", "烧焦增量，如 -0.5 / 0.2");

        TextView adv = new TextView(this);
        adv.setText("—— 高级参数（增量，0 表示不变）——");
        adv.setTextSize(14);
        adv.setPadding(0, dp(14), 0, dp(6));
        root.addView(adv);

        saturationEdit = addNumberRow(root, "饱和度增量（默认 0）", "玻璃颜色饱和度，如 0.3 更鲜艳 / -0.2 更灰");
        alphaEdit = addNumberRow(root, "透明度增量（默认 0）", "玻璃透明度，如 +0.2 更通透 / -0.1 更实");
        tintREdit = addNumberRow(root, "色调 R 增量（默认 0）", "玻璃染色红色，如 +0.3 偏红");
        tintGEdit = addNumberRow(root, "色调 G 增量（默认 0）", "玻璃染色绿色，如 +0.3 偏绿");
        tintBEdit = addNumberRow(root, "色调 B 增量（默认 0）", "玻璃染色蓝色，如 +0.3 偏蓝");
        edgeEdit = addNumberRow(root, "边缘厚度增量（默认 0）", "玻璃边缘描边厚度，如 +40 更粗");
        reflectEdit = addNumberRow(root, "反射强度增量（默认 0）", "玻璃反光强度，如 +0.5 更亮");
        lightEdit = addNumberRow(root, "方向光强度增量（默认 0）", "顶部光照强度，如 +0.5 更亮");
        bgSatEdit = addNumberRow(root, "背景色饱和度增量（默认 0）", "背景采样饱和度，如 +0.5");
        bgBrightEdit = addNumberRow(root, "背景色亮度增量（默认 0）", "背景采样亮度，如 +0.5 更亮");
        panelScaleEdit = addNumberRow(root, "下拉背景缩放强度（默认 1.0）", "下拉通知栏时壁纸/背景的缩放程度：0=无缩放，1=原样，2=加倍");

        rowGlassSwitch = addSwitchRow(root, "通知行强制玻璃", "开启：通知卡走系统玻璃渲染管线（默认开）");
        sdfClampSwitch = addSwitchRow(root, "玻璃层高度钳制", "开启：最后一条玻璃层对齐内容高度（默认开，修复截断）");

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(20), 0, 0);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(v -> save());
        buttons.addView(save, lp(1));

        Button saveRestart = new Button(this);
        saveRestart.setText("保存并重启 SystemUI");
        saveRestart.setOnClickListener(v -> {
            if (save()) {
                restartSystemUi();
            }
        });
        buttons.addView(saveRestart, lp(1));

        Button reset = new Button(this);
        reset.setText("恢复默认");
        reset.setOnClickListener(v -> {
            boolean ok = writeViaSu("settings delete system " + MainHook.CONFIG_KEY);
            if (!ok && Settings.System.canWrite(this)) {
                Settings.System.putString(getContentResolver(),
                        MainHook.CONFIG_KEY, null);
                ok = true;
            }
            loadCurrent();
            Toast.makeText(this, ok ? "已恢复默认" : "恢复失败（无 root 权限）",
                    Toast.LENGTH_SHORT).show();
        });
        buttons.addView(reset, lp(1));

        root.addView(buttons);
    }

    private EditText addNumberRow(LinearLayout root, String label, String desc) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        root.addView(tv);

        TextView dv = new TextView(this);
        dv.setText(desc);
        dv.setTextSize(11);
        dv.setPadding(0, 0, 0, dp(2));
        root.addView(dv);

        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setGravity(Gravity.END);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        root.addView(et);
        return et;
    }

    private Switch addSwitchRow(LinearLayout root, String label, String desc) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv);

        TextView dv = new TextView(this);
        dv.setText(desc);
        dv.setTextSize(11);
        root.addView(dv);

        Switch sw = new Switch(this);
        root.addView(sw);
        return sw;
    }

    private LinearLayout.LayoutParams lp(float weight) {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void loadCurrent() {
        try {
            String json = Settings.System.getString(getContentResolver(),
                    MainHook.CONFIG_KEY);
            if (json == null || json.trim().isEmpty()) {
                json = "{}";
            }
            org.json.JSONObject o = new org.json.JSONObject(json);
            radiusEdit.setText(String.valueOf(o.optInt("radius", 40)));
            panelEdit.setText(String.valueOf(o.optInt("panelBlur", 50)));
            brightnessEdit.setText(String.valueOf(o.optDouble("brightness", -0.05)));
            darkerEdit.setText(String.valueOf(o.optDouble("darker", -0.08)));
            iorEdit.setText(String.valueOf(o.optDouble("ior", 0.20)));
            burnEdit.setText(String.valueOf(o.optDouble("burn", -0.25)));
            saturationEdit.setText(String.valueOf(o.optDouble("saturation", 0)));
            alphaEdit.setText(String.valueOf(o.optDouble("alpha", 0)));
            tintREdit.setText(String.valueOf(o.optDouble("tintR", 0)));
            tintGEdit.setText(String.valueOf(o.optDouble("tintG", 0)));
            tintBEdit.setText(String.valueOf(o.optDouble("tintB", 0)));
            edgeEdit.setText(String.valueOf(o.optDouble("edgeThickness", 0)));
            reflectEdit.setText(String.valueOf(o.optDouble("reflectStrength", 0)));
            lightEdit.setText(String.valueOf(o.optDouble("lightIntensity", 0)));
            bgSatEdit.setText(String.valueOf(o.optDouble("bgSat", 0)));
            bgBrightEdit.setText(String.valueOf(o.optDouble("bgBright", 0)));
            panelScaleEdit.setText(String.valueOf(o.optDouble("panelScale", 1.0)));
            rowGlassSwitch.setChecked(o.optBoolean("rowGlass", true));
            sdfClampSwitch.setChecked(o.optBoolean("sdfClamp", true));
        } catch (Throwable t) {
            Toast.makeText(this, "读取配置失败: " + t, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean save() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("radius", clamp(parseInt(radiusEdit, 40), 0, 500));
            o.put("panelBlur", clamp(parseInt(panelEdit, 50), 0, 100));
            o.put("brightness", parseFloat(brightnessEdit, -0.05f));
            o.put("darker", parseFloat(darkerEdit, -0.08f));
            o.put("ior", parseFloat(iorEdit, 0.20f));
            o.put("burn", parseFloat(burnEdit, -0.25f));
            o.put("saturation", parseFloat(saturationEdit, 0f));
            o.put("alpha", parseFloat(alphaEdit, 0f));
            o.put("tintR", parseFloat(tintREdit, 0f));
            o.put("tintG", parseFloat(tintGEdit, 0f));
            o.put("tintB", parseFloat(tintBEdit, 0f));
            o.put("edgeThickness", parseFloat(edgeEdit, 0f));
            o.put("reflectStrength", parseFloat(reflectEdit, 0f));
            o.put("lightIntensity", parseFloat(lightEdit, 0f));
            o.put("bgSat", parseFloat(bgSatEdit, 0f));
            o.put("bgBright", parseFloat(bgBrightEdit, 0f));
            o.put("panelScale", parseFloat(panelScaleEdit, 1.0f));
            o.put("rowGlass", rowGlassSwitch.isChecked());
            o.put("sdfClamp", sdfClampSwitch.isChecked());
            String json = o.toString();

            // 1) root write (settings via su) - proven to work
            if (writeViaSu("settings put system " + MainHook.CONFIG_KEY
                    + " '" + json + "'")) {
                Toast.makeText(this, "已保存（实时生效）", Toast.LENGTH_SHORT).show();
                return true;
            }
            // 2) fallback: WRITE_SETTINGS special permission
            if (Settings.System.canWrite(this)) {
                Settings.System.putString(getContentResolver(),
                        MainHook.CONFIG_KEY, json);
                Toast.makeText(this, "已保存（实时生效）", Toast.LENGTH_SHORT).show();
                return true;
            }
            Toast.makeText(this,
                    "保存失败：请在 Magisk 中允许本应用的 root 权限"
                            + "（或授予\"修改系统设置\"权限）",
                    Toast.LENGTH_LONG).show();
            return false;
        } catch (Throwable t) {
            Toast.makeText(this, "保存失败: " + t, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    /** Run a shell command through su with a 5s timeout; true on exit 0. */
    private boolean writeViaSu(String command) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Restart SystemUI via root; works when Magisk grants the app su. */
    private void restartSystemUi() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "killall com.android.systemui"});
            p.waitFor();
            Toast.makeText(this, "已重启 SystemUI", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "重启失败，请手动重启 SystemUI", Toast.LENGTH_LONG).show();
        }
    }

    private static int parseInt(EditText et, int def) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static float parseFloat(EditText et, float def) {
        try {
            return Float.parseFloat(et.getText().toString().trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
