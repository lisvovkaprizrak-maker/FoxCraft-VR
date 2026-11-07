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
    private LinearLayout content, sidebar;

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
        button.setMinHeight(dp(44));
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
        settings = getSharedPreferences("foxcraft_menu_preview", MODE_PRIVATE);
        mode = settings.getString("mode", "vr");
        tab = settings.getString("tab", "Главная");
        version = Math.max(0, Math.min(RELEASE_SAMPLES.length - 1,
            settings.getInt("version", 0)));
        status = "Нажми кнопки меню и проверь, удобно ли ими пользоваться.";
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header = horizontal(Color.TRANSPARENT);
        TextView logo = text("🦊  FOXCRAFT", 24, ORANGE, true);
        header.addView(logo, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView descriptor = text("МЕНЮ · ТЕСТОВАЯ ВЕРСИЯ", 13, MUTED, true);
        descriptor.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(descriptor);
        root.addView(header);
        gap(root, 5);

        TextView warning = text(
            "ПРОТОТИП МЕНЮ • Не является Unity/QuestCraft APK. Аккаунты и Minecraft здесь не запускаются.",
            11, WHITE, false);
        warning.setPadding(dp(12), dp(5), dp(12), dp(5));
        warning.setBackground(background(Color.rgb(90, 53, 24), ORANGE));
        root.addView(warning, new LinearLayout.LayoutParams(-1, dp(34)));
        gap(root, 12);

        LinearLayout frame = horizontal(Color.TRANSPARENT);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        sidebar = vertical(PANEL, 10);
        frame.addView(sidebar, new LinearLayout.LayoutParams(dp(184), -1));
        label(sidebar, "НАВИГАЦИЯ", 12, MUTED, true);
        gap(sidebar, 8);
        for (String name : new String[]{"Главная", "Аккаунты", "Сборки", "Моды", "Настройки"}) {
            Button item = button(name, name.equals(tab), () -> changeTab(name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
            lp.bottomMargin = dp(8);
            sidebar.addView(item, lp);
        }

        View space = new View(this);
        frame.addView(space, new LinearLayout.LayoutParams(dp(13), 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = vertical(PANEL, 17);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        frame.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));

        if ("Главная".equals(tab)) showHome();
        else if ("Аккаунты".equals(tab)) showAccounts();
        else if ("Сборки".equals(tab)) showInstances();
        else if ("Моды".equals(tab)) showMods();
        else showSettings();

        gap(root, 10);
        LinearLayout bottom = horizontal(PANEL);
        bottom.setPadding(dp(10), dp(6), dp(10), dp(6));
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(65)));
        TextView state = text(status, 12, MUTED, false);
        bottom.addView(state, new LinearLayout.LayoutParams(0, -1, 1));
        Button play = button("▶ ИГРАТЬ", true, () ->
            notice("Это тест меню. Игровой движок, авторизация и миры ещё не подключены."));
        bottom.addView(play, new LinearLayout.LayoutParams(dp(172), dp(48)));
    }

    private void title(String heading, String subtitle) {
        label(content, heading, 23, WHITE, true);
        gap(content, 5);
        label(content, subtitle, 12, MUTED, false);
        gap(content, 15);
    }

    private void showHome() {
        title("Выбор режима игры", "FoxCraft сохраняет управление отдельно для VR и телефона.");
        LinearLayout modes = horizontal(Color.TRANSPARENT);
        addButton(modes, "🥽  VR", mode.equals("vr"), () -> changeMode("vr"), 1);
        addButton(modes, "📱  ТЕЛЕФОН", mode.equals("phone"),
            () -> changeMode("phone"), 1);
        content.addView(modes);
        gap(content, 12);
        LinearLayout versionRow = horizontal(INSET);
        versionRow.setPadding(dp(7), dp(5), dp(7), dp(5));
        content.addView(versionRow);
        TextView v = text("Minecraft Java: " + RELEASE_SAMPLES[version], 16, WHITE, true);
        versionRow.addView(v, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button prev = button("◀", false, () -> changeVersion(-1));
        Button next = button("▶", false, () -> changeVersion(1));
        versionRow.addView(prev, new LinearLayout.LayoutParams(dp(50), dp(45)));
        versionRow.addView(next, new LinearLayout.LayoutParams(dp(50), dp(45)));
        gap(content, 12);
        label(content, mode.equals("vr")
            ? "VR — будущий запуск через QuestCraft / OpenXR"
            : "ТЕЛЕФОН — будущий запуск через отдельный 2D-движок", 14, ORANGE, true);
        gap(content, 6);
        label(content, "Версии в этом прототипе — примеры. " +
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
