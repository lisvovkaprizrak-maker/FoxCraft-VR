package dev.foxcraft.preview;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class PreviewActivity extends Activity {
    private static final int BG = Color.rgb(12, 13, 18);
    private static final int PANEL = Color.rgb(26, 28, 34);
    private static final int INSET = Color.rgb(34, 36, 42);
    private static final int ORANGE = Color.rgb(255, 122, 28);
    private static final int WHITE = Color.rgb(249, 244, 236);
    private static final int MUTED = Color.rgb(175, 177, 185);
    private static final String[] RELEASE_SAMPLES = {
        "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4"
    };
    private SharedPreferences settings;
    private String mode, tab, status;
    private int version;
    private LinearLayout content;

    private int dp(float d) {
        return Math.round(getResources().getDisplayMetrics().density * d);
    }

    private GradientDrawable background(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(13));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(sp);
        label.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private LinearLayout vertical(int color, int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        if (color != Color.TRANSPARENT) layout.setBackground(background(color, 0));
        return layout;
    }

    private LinearLayout horizontal(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        if (color != Color.TRANSPARENT) layout.setBackground(background(color, 0));
        return layout;
    }

    private void gap(LinearLayout parent, int height) {
        View spacer = new View(this);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void label(LinearLayout parent, String value, int sp, int color, boolean bold) {
        parent.addView(text(value, sp, color, bold));
    }

    private Button button(String name, boolean active, Runnable callback) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(name);
        button.setTextColor(WHITE);
        button.setTextSize(13);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setBackground(background(active ? ORANGE : INSET, active ? ORANGE : 0));
        button.setOnClickListener(view -> callback.run());
        return button;
    }

    private void addButton(LinearLayout row, String value, boolean active,
                           Runnable action, int weight) {
        Button b = button(value, active, action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, dp(45), weight);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(b, params);
    }

    private void persist() {
        settings.edit().putString("mode", mode)
            .putString("tab", tab).putInt("version", version).apply();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Use the full landscape canvas; Android status/navigation bars in
        // screenshots consumed nearly a third of the already short viewport.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        settings = getSharedPreferences("foxcraft_menu_preview", MODE_PRIVATE);
        mode = settings.getString("mode", "vr");
        tab = settings.getString("tab", "Главная");
        version = Math.max(0, Math.min(RELEASE_SAMPLES.length - 1,
            settings.getInt("version", 0)));
        status = "Тест меню: проверь переключатели и навигацию.";
        render();
    }

    private void notice(String value) {
        status = value;
        render();
    }

    private void changeTab(String next) {
        tab = next;
        persist();
        render();
    }

    private void changeMode(String next) {
        mode = next;
        persist();
        status = "Выбран режим: " + (mode.equals("vr") ? "VR" : "Телефон")
            + ". Это демонстрация интерфейса — Minecraft не запущен.";
        render();
    }

    private void changeVersion(int step) {
        int size = RELEASE_SAMPLES.length;
        version = ((version + step) % size + size) % size;
        persist();
        status = "Выбрана демонстрационная версия " + RELEASE_SAMPLES[version]
            + ". Загрузка игры пока не подключена.";
        render();
    }

    private void render() {
        // The previous menu used an unscrollable vertical sidebar with five
        // 46dp buttons. On a landscape phone less than ~350dp high, three
        // buttons disappeared. Use one compact scrollable tab row instead.
        LinearLayout root = vertical(Color.TRANSPARENT, 0);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = horizontal(Color.TRANSPARENT);
        ImageView fox = new ImageView(this);
        fox.setImageResource(R.drawable.foxcraft_logo);
        fox.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(fox, new LinearLayout.LayoutParams(dp(43), dp(43)));
        TextView logo = text("  FOXCRAFT", 20, ORANGE, true);
        header.addView(logo, new LinearLayout.LayoutParams(0, dp(45), 1));
        TextView caption = text("ТЕСТ МЕНЮ", 11, MUTED, true);
        caption.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(caption, new LinearLayout.LayoutParams(dp(102), dp(45)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(47)));
        gap(root, 5);

        HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setFillViewport(true);
        LinearLayout tabs = horizontal(PANEL);
        tabs.setPadding(dp(5), dp(3), dp(5), dp(3));
        for (String name : new String[]{"Главная", "Аккаунты", "Сборки", "Моды", "Настройки"}) {
            Button item = button(name, name.equals(tab), () -> changeTab(name));
            item.setTextSize(12);
            item.setMinHeight(0);
            item.setMinimumHeight(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(112), dp(39));
            lp.setMargins(dp(2), 0, dp(2), 0);
            tabs.addView(item, lp);
        }
        tabsScroll.addView(tabs, new HorizontalScrollView.LayoutParams(-2, -1));
        root.addView(tabsScroll, new LinearLayout.LayoutParams(-1, dp(46)));
        gap(root, 7);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        content = vertical(PANEL, 13);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        // Every section stays reachable on compact landscape screens. Only
        // the middle content scrolls; header, tabs and Play remain in view.
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if ("Главная".equals(tab)) showHome();
        else if ("Аккаунты".equals(tab)) showAccounts();
        else if ("Сборки".equals(tab)) showInstances();
        else if ("Моды".equals(tab)) showMods();
        else showSettings();

        gap(root, 7);
        LinearLayout bottom = horizontal(PANEL);
        bottom.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView state = text(status, 11, MUTED, false);
        state.setSingleLine(true);
        state.setEllipsize(TextUtils.TruncateAt.END);
        bottom.addView(state, new LinearLayout.LayoutParams(0, -1, 1));
        Button play = button("▶ ИГРАТЬ", true, () ->
            notice("Тест интерфейса: Minecraft и авторизация пока не подключены."));
        play.setMinHeight(0);
        play.setMinimumHeight(0);
        bottom.addView(play, new LinearLayout.LayoutParams(dp(126), dp(43)));
    }

    private void title(String heading, String subtitle) {
        label(content, heading, 20, WHITE, true);
        gap(content, 5);
        label(content, subtitle, 12, MUTED, false);
        gap(content, 9);
    }

    private void showHome() {
        title("Выбор режима игры", "FoxCraft сохраняет управление отдельно для VR и телефона.");
        LinearLayout modes = horizontal(Color.TRANSPARENT);
        addButton(modes, "🥽  VR", mode.equals("vr"), () -> changeMode("vr"), 1);
        addButton(modes, "📱  ТЕЛЕФОН", mode.equals("phone"),
            () -> changeMode("phone"), 1);
        content.addView(modes);
        gap(content, 8);
        LinearLayout versionRow = horizontal(INSET);
        versionRow.setPadding(dp(7), dp(5), dp(7), dp(5));
        content.addView(versionRow);
        TextView v = text("Minecraft Java: " + RELEASE_SAMPLES[version], 16, WHITE, true);
        versionRow.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button prev = button("◀", false, () -> changeVersion(-1));
        Button next = button("▶", false, () -> changeVersion(1));
        versionRow.addView(prev, new LinearLayout.LayoutParams(dp(50), dp(45)));
        versionRow.addView(next, new LinearLayout.LayoutParams(dp(50), dp(45)));
        gap(content, 8);
        label(content, mode.equals("vr")
            ? "VR — будущий запуск через QuestCraft / OpenXR"
            : "ТЕЛЕФОН — будущий запуск через отдельный 2D-движок", 14, ORANGE, true);
        gap(content, 6);
        label(content, "Это тест интерфейса. Версии здесь демонстрационные. " +
            "В основном проекте есть загрузка официального списка Mojang.", 12, MUTED, false);
    }

    private void showAccounts() {
        title("Аккаунты Microsoft", "Сохранён раздел меню QuestCraft.");
        label(content, "⚪  Аккаунт не добавлен", 17, WHITE, true);
        gap(content, 12);
        addButton(contentRow(), "＋ ДОБАВИТЬ АККАУНТ", true,
            () -> notice("Авторизация отключена в APK-прототипе. Не вводи сюда пароль."), 1);
        gap(content, 9);
        label(content, "В реальном лаунчере используется существующий " +
            "механизм авторизации Pojlib / QuestCraft.", 12, MUTED, false);
    }

    private LinearLayout contentRow() {
        LinearLayout row = horizontal(Color.TRANSPARENT);
        content.addView(row);
        return row;
    }

    private void showInstances() {
        title("Сборки Minecraft", "Проверка переключения экрана и элементов управления.");
        label(content, "Пример: Fabric 1.21.5  •  Vanilla 1.20.1", 15, WHITE, true);
        gap(content, 12);
        LinearLayout row = contentRow();
        addButton(row, "＋ СОЗДАТЬ", true, () ->
            notice("Создание сборки подключим в Unity-лаунчере."), 1);
        addButton(row, "⚙ УПРАВЛЕНИЕ", false, () ->
            notice("Управление сборками в этом прототипе отключено."), 1);
    }

    private void showMods() {
        title("Моды и модпаки", "Функции QuestCraft останутся в полной сборке.");
        label(content, "Fabric  •  Моды  •  Модпаки  •  Ресурспаки", 16, WHITE, true);
        gap(content, 12);
        LinearLayout row = contentRow();
        addButton(row, "ПОИСК МОДОВ", true, () ->
            notice("Поиск модов требует Pojlib; здесь доступен только интерфейс."), 1);
        addButton(row, "УСТАНОВЛЕННЫЕ", false, () ->
            notice("Список модов не подключён к прототипу."), 1);
    }

    private void showSettings() {
        title("Настройки FoxCraft", "Оформление и переходы между экранами.");
        label(content, "Режим: " + (mode.equals("vr") ? "VR" : "Телефон"), 15, WHITE, true);
        gap(content, 10);
        label(content, "Память Minecraft: 2048 МБ (пример)", 14, MUTED, false);
        gap(content, 10);
        LinearLayout row = contentRow();
        addButton(row, "🟠 ОРАНЖЕВАЯ ТЕМА", true,
            () -> notice("Тема FoxCraft: оранжевая."), 1);
        addButton(row, "О ПРОЕКТЕ", false,
            () -> notice("FoxCraft — лаунчер на основе QuestCraft. Это только тестовый экран."), 1);
    }
}
