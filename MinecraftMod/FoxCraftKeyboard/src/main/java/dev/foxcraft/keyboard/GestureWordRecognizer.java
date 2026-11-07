package dev.foxcraft.keyboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline, privacy-preserving shape-writing recognizer. It compares the entire
 * stroke with the shapes of candidate words on the CURRENT keyboard geometry.
 * Crossing a letter does not force that letter into the resulting word.
 * Controller speed and intermediate-key intersections are ignored.
 *
 * This is an initial local vocabulary, not the Yandex dictionary or engine.
 */
public final class GestureWordRecognizer {
    public record Point(double x, double y) {}
    private record Match(String word, double score) {}

    // Roughly frequent chat, conversation and Minecraft words. Adding entries
    // here extends coverage without changing any controller or GUI logic.
    private static final String RU = """
        привет пока спасибо пожалуйста хорошо отлично нормально да нет как дела что это ты я мы вы они он она оно мне тебя меня тебе мой моя твой наш ваши наша друг друзья приветик здравствуй здравствуйте добрый доброе утро день вечер ночь спокойной удачи счастливо увидимся позже скоро сегодня завтра вчера сейчас потом раньше всегда никогда иногда часто редко очень сильно совсем просто почти точно правда реально конечно может возможно наверное наверно вроде если когда где куда откуда почему зачем потому поэтому здесь тут там дома домой рядом далеко близко слева справа сверху снизу назад вперед вперёд вверх вниз всё все всего всем ещё еще уже снова опять тоже также только либо или но а и с в на по за из от до про под над для без между перед после при через вокруг около среди вместе кроме тогда значит надо нужно можно нельзя хочу хочешь хочет хотим хотеть люблю любит любим любить нравится супер круто класс классно лучший лучше хуже плохой плохо хороший хорошая хорошее красивые красивый красивое большое большой большая маленький маленькая много мало чуть больше меньше самый самая самые первый второй третий один два три четыре пять шесть семь восемь девять десять ноль раз никто кто каждый другой другая другие ничего весь любая любой такую такой такая такое такие этого этому этой этим этих тому которое который которая которые каких какие какой какую сколько столько скажи говори говорю говоришь пишет пишу пишешь напиши написать сообщение сообщения текст слово слова буквы буква язык русский английский перевод переводчик понятно понимаю понял поняла понимаешь объясни объяснить проверь проверить тест игра играть играю играешь сыграть запускай запусти запуск работает работают работа работало сделай делай делать сделали готов готово готовый готова пробуем пробуй попробуй попробовать получилось получается выйдет получится помоги помощь поможешь помогать прошу войти выйти выход вход открыть закрыть открывай закрывай включи выключи нажми держи держать отпусти отпускать веди веду ввод вводить ввести ввёл вводится исправить исправь исправление ошибка ошибки правильно неправильный неправильно верно верный неверно случай случайно повтори повторить сначала конец заканчивай продолжай продолжить продолжаем останови остановка остановиться стоп подожди стой затем дальше слушай смотри видишь вижу видно показывает покажи показать изображение фото скрин скриншот видео звук слышно слышу слышишь говорит разговор чат чате чатик клавиатура клавиатуре клавиатуры контроллер контроллеры рука руки левая левый правый правая левую правую обе обеих одной двумя палец пальцем курок триггер нажал нажать нажатие движение движением проведи провести вожу провёл проведением свайп свайпом траектория траекторию распознавание распознать варианты вариант выбор выбираю выбрать выбирай предложение предложения письмо отправить отправляй отправил послать майнкрафт майнкрафте квесткрафт фокскрафт мир миру мире мира мобы моб игрок игроки сервер сервера сервере зайди заходи присоединяйся подключись подключение подключиться играли строить строю дом строим блок блоки камень земля дерево лес вода огонь свет светло темно дождь снег солнце луна звезда звёзды крипер зомби скелет паук эндермен дракон алмазы железо золото меч кирка лопата топор броня инвентарь крафт крафтить предмет предметы взять дай дайте принеси идём иди пойдем пойдём ходи ходить ходим бегом беги бежать летать лети прыгай прыгнуть падать упасть сюда туда телепорт телепортируй меня помогите приветствие добро пожаловать всем ребята ребят парень девочка человек люди человека людей имя имена зовут есть нету было был были будет будем быть стану стал стала стою сидеть лежать делать еда пить хлеб мясо яблоко картошка морковь рыба костёр печка сундук дверь окно крыша пол стены мост дорога путь карта координаты координата позиция точка кнопка кнопки настройки настроить настроил настройка профиль профили версия обновление обновить обнови новая новый новое старый старая всё нормально большое спасибо очень хорошо всем помогите тут как вы как играешь крутая красиво смешно смешной смешная весело скучно интересно интересный готов играть погнали го давай давайте начинается начинай начать ждём жду ждите ждать минуту минута минут секунд секундочку секунда быстро медленно скорость срочно стоит стоишь спасибо всем очень приятно приятно познакомиться знаком знакомы рад рада радость грусть грустно здоровье сила сильный умный умница молодец молодцы поздравляю поздравления удалось надоело передумал передумала подумал думаю думаешь думаем расскажи рассказывать рассказывай подробнее подробности объяснение объясняю ладно ладушки окей ок хорошо договорились ясно ясное очевидно не знаю знаю знаешь знает знаем неизвестно известный возможно уверен уверена уверены думаю может надо снова пробовать проблема проблемы решение решения решить решим решаем новый тест проверка проверяем тестируем тест тесты проверим результат результаты правильный верное исправлено сделано
        """;
    private static final String EN = """
        hello hi hey bye goodbye thanks thank please yes no ok okay sure cool great nice good bad awesome amazing perfect welcome friend friends buddy brother sister family people player players name names chat text word words typing keyboard swipe gesture controller trigger release hold left right hands hand both touch cursor pointer game games gaming minecraft questcraft vivecraft foxcraft quest virtual reality vr headset world server servers join leave connect online offline login start stop begin end continue restart reset cancel save load open close settings options test testing check fix fixed build built work works working broken update version mod mods install download layout language english russian morning afternoon evening night today tomorrow yesterday now soon later maybe probably definitely sometimes always never again only also really very much many little less more best better worse worst here there where when why what who which whose how can could will would should might must do does did done doing make made making get got getting go going went gone come coming came back right wrong correct error issue problem solution help helping sorry excuse free busy ready waiting good luck well done nice shot nice try no problem all good alright sound sounds music audio voice microphone mic mute unmute screen display button buttons press tap touch touchpad thumbstick joystick grip shoulder control controls finger fingers ray type typed suggestion suggestions candidate candidates choose choice select correct correction predictions predict predicted dictionary words this that these those your yours mine my our ours their theirs its his her he she they them us you i me we are is am be been being were was have has had with without about around before after between through over under above below inside outside into from for to at in on by a an the and or but because so if as than then too enough need want wanted wish love loved like liked dislike hate know knew known think thought thinking feel feeling hope hoping believe game crafting craft mining mine stone wood dirt water fire house home base cave armor sword shield pickaxe axe food bread meat fish apple diamond iron gold copper redstone nether end portal village villager zombie skeleton creeper enderman dragon follow following meet meeting find found search searching message messages write wrote written tell told say said speak speaking talk talking listen listening look looking see seeing saw watching try trying tried happy sad fun funny beautiful pretty simple easy hard difficult fast slow clear dark light day time moment minute second seconds next last first one two three four five six seven eight nine ten zero
        """;

    private static final String RU_EXTRA = """
        куплю донки купить купи купим купит купишь купят купил купила купили покупка покупки покупаю покупает покупать покупал покупала покупали покупаем покупаете покупают покупатель продавец продам продать продаю продаёт продал продала продаём продажа деньги денег денежки цена цены ценник дорого дешево дёшево дешевле скидка магазин магазины заказать заказ заказал заказала заказали доставка доставить доставили товар товары вещи вещь вещами предметов набор наборы создать создаю создаём создал создала создавали создавай создавать построить построил построила строили построили построю построим строишь строит строят строительство постройка разрушить разрушил сломать сломал ломать ломаю добыть добуду добываю добыча добыли накопать накопал выкопать выкопал собрать собрал собрала соберём соберу собираю собирать собрали искать ищу ищешь ищет ищем нашли найти находить находишь находка нашёл нашла нашел получил получила получили получу получишь получать отдать отдал отдала отдавай отдаю отдам отдадим подарок подарю подарить подарил подарила дарить принести принёс принесла принесу забрать забрал забрала забери забирай забираю перенести перенёс перенесла переместить перемести перевести перевёл перевела поменять поменяй поменял поменяла менять меняю обмен обменять обменяй обмениваться поделиться делись поделись пользуюсь использовать использую использовал использовали установить установил установила устанавливаю установка скачать скачал скачала скачай скачиваем скачивание загрузить загрузи загружаю загрузка загрузился загрузилась загрузилось загрузили распаковать распакуй распаковал распаковка архив архивы файл файлы файлом папка папку папки папке ссылка ссылку ссылки обновился обновилась обновилось исправился исправилась исправилось исправляются исправляю исправляем заработал заработала заработало заработали работает работаю работаем работайте работаешь работоспособность ошибка ошибся ошиблась ошибаюсь ошибается исправляй исправим исправишь починить почини починил починила починили сломалось сломался сломалась неработает неработающий переключить переключи переключатель переключение поменялось изменилось изменился изменилась изменить измени изменения изменять изменю включать включается включился включилась включилось выключать выключается выключился выключилась выключилось запустить запускаю запускается запустился запустилась запустилось перезапуск перезапустить перезапусти перезапустил настрой настройки настроек настройках настроить настроенный установить переустановить переустановил переустановила проверять проверяю проверяют проверили проверила проверил проверить протестировать протестируй тестирование тестовый тестовая тестовое результат результатов результаты удобный удобно неудобно неудобный удобнее поднять подними поднял поднялась поднялось опустить опусти ниже повыше выше пониже расположение расположить расположи положение передвинуть передвинь сдвинуть сдвинь немного немножко чуть чуть-чуть слишком сильно поближе подальше близко далеко далеко-то экран экране окна окном окошко окошке меню кнопках кнопкой нажимать нажимается нажимали нажимал нажимала нажимает нажимают нажимаю касание касаться касается нажатием триггеры контроллера контроллеру контроллером контроллерах контроллерами левым правым левом правом левое правое левыми правыми палец пальцы указатель указатели луч лучи лучом лучами курсор курсоры сенсор сенсоры джойстик джойстики движение движется двигать двигаю двигается двигался двигалась двигались удерживать удерживай удерживаю удерживал удерживала удерживается удержание долго долгий долгое коротко короткий быстро быстрее медленнее медленный плавно плавный точный точно точнее точность траектории траектория траекторию жест жесты жестом свайпы свайпать свайпаю свайпом вводить ввожу ввёл ввела ввели набрать набери набрал набрала набрали набираю набираешь набирает напечатать напечатай напечатал напечатала пишем пишут написала написал написали пишете пишет написано сообщение сообщении сообщения сообщений отправка отправлен отправлено отправила отправил отправили пришли пришёл пришел пришла пришло пришлите отправьте передай передать передал передала передали ответ ответа ответить ответь отвечай отвечаю ответил ответила отвечают вопрос вопросы вопросов вопросик спросить спроси спросил спросила спрашиваю спрашивает узнать узнал узнала узнали узнали почему-то почему нибудь кто-то что-то где-то когда-то какой-то какой-нибудь кому-то кому-нибудь чего-то кому кого кого-то чьё чей чья чьи какой какая какого какой-нибудь разные разный разная разное разных любой любые любым любому любыми моя моё мои твоё твои нашего наших нашему вашим вашего ваших свой своё свои своего своей своих себя собой себе себя видеть видела видел видели смотрел смотрела смотрели смотрю смотрим слушал слушала слушали слышал слышала слышали поговорить поговорим поговорю общаться общаюсь общаемся разговаривать разговариваю рассказывать рассказываю рассказал рассказала рассказали расскажи расскажите объяснять объясняю объяснил объяснила объяснили объясни объясните повторять повторяю повторил повторила повторили повторите повтори понять понимать понял поняла поняли понятно непонятно понятнее яснее именно приблизительно примерно возможно невозможно вероятно может могут можем можешь можете сможет сможешь смогу сможем смог смогла смогли получится получиться получалось получалось бы удалось удастся удалось понравилось понравился понравилась нравится нравятся понравится люблю любим любимая любимый любимое любимые любить любишь любите любимые хотеть хочу хочется хотел хотела хотели хотите хотят захочу захотел захотела необходимо требуется требуются нужен нужна нужные нужное нужнее больше всего меньше всего немного немало слишком достаточно недостаточно крайне постоянно иногда периодически обязательно обязательно-то сначала сперва начало начальный начинается началось начался началась начинается начинаю заканчивается закончилось закончился закончилась закончить закончи заканчиваю конец конца концу следующий следующая следующее следующие предыдущий предыдущая предыдущие последний последняя последнее последние недавно давно сейчас сегодня вчера завтра послезавтра утром вечером ночью днём днем позже пораньше попозже часов часа часов минут минуты минуту секунды секунд секундочку времени время разок разочек раза раз третий четвёртый пятый шестой седьмой восьмой девятый десятый одиннадцать двенадцать тринадцать четырнадцать пятнадцать двадцать тридцать сорок пятьдесят сто тысяча тысяч миллион миллионы много много-то несколько сколько сколько-то столько столько-то один одна одно одни двух двум тремя четыре шесть семь десять
        """;
    private static final String[] RU_WORDS = (RU_EXTRA + " " + RU).strip().split("\\s+");
    private static final String[] EN_WORDS = EN.strip().split("\\s+");

    private GestureWordRecognizer() {}

    public static List<String> suggest(KeyboardLayout.Panel panel, List<Point> stroke,
                                       int maxResults) {
        if (panel == null || stroke == null || stroke.size() < 2 || maxResults <= 0)
            return List.of();
        Map<String, Point> positions = new HashMap<>();
        for (KeyboardLayout.Key key : panel.keys()) {
            if (key.command() != KeyboardLayout.Command.TYPE || key.value().length() != 1)
                continue;
            String letter = key.value().toLowerCase(Locale.ROOT);
            if (!Character.isLetter(letter.charAt(0))) continue;
            positions.put(letter, new Point(key.x() + key.width() / 2.0,
                key.y() + key.height() / 2.0));
        }
        boolean russian = positions.containsKey("й");
        if (russian) positions.put("ё", positions.get("е"));
        String[] dictionary = russian ? RU_WORDS : EN_WORDS;
        List<Point> trace = resample(stroke, 24);
        if (trace.size() < 2) return List.of();
        Point start = stroke.get(0), end = stroke.get(stroke.size() - 1);
        double sx = Math.max(9.0, panel.width() / (russian ? 12.0 : 10.0));
        double sy = Math.max(9.0, panel.height() / 7.0);
        List<Match> matches = new ArrayList<>();
        for (int rank = 0; rank < dictionary.length; rank++) {
            String word = dictionary[rank];
            if (word.length() < 2 || word.length() > 20) continue;
            List<Point> letters = new ArrayList<>(word.length());
            boolean known = true;
            for (int i = 0; i < word.length(); i++) {
                Point pos = positions.get(word.substring(i, i+1));
                if (pos == null) { known = false; break; }
                letters.add(pos);
            }
            if (!known) continue;
            double first = squared(start, letters.get(0), sx, sy);
            double last = squared(end, letters.get(letters.size()-1), sx, sy);
            if (first > 2.5 || last > 2.5) continue;
            List<Point> expected = resample(letters, trace.size());
            double shape = 0;
            for (int j = 0; j < trace.size(); j++)
                shape += squared(trace.get(j), expected.get(j), sx, sy);
            shape /= trace.size();
            double score = shape + 0.27 * (first + last) +
                Math.log1p(rank) * 0.008;
            if (score <= 2.2) matches.add(new Match(word, score));
        }
        matches.sort(Comparator.comparingDouble(Match::score));
        List<String> result = new ArrayList<>();
        for (Match candidate : matches) {
            if (!result.contains(candidate.word())) result.add(candidate.word());
            if (result.size() >= maxResults) break;
        }
        return List.copyOf(result);
    }

    private static double squared(Point a, Point b, double sx, double sy) {
        double dx = (a.x()-b.x())/sx, dy = (a.y()-b.y())/sy;
        return dx*dx + dy*dy;
    }

    /** Arc-length resampling ignores hand speed and frame-rate differences. */
    private static List<Point> resample(List<Point> path, int count) {
        if (path.size() < 2) return List.of();
        double[] d = new double[path.size()];
        for (int i=1; i<path.size(); i++)
            d[i] = d[i-1] + Math.hypot(path.get(i).x()-path.get(i-1).x(),
                path.get(i).y()-path.get(i-1).y());
        double total = d[d.length-1];
        if (total < 0.01) return List.of();
        List<Point> samples = new ArrayList<>(count);
        int seg = 1;
        for (int i=0; i<count; i++) {
            double target = total*i/(count-1);
            while (seg<d.length-1 && d[seg] < target) seg++;
            double span = d[seg]-d[seg-1];
            double t = span > 1e-9 ? Math.max(0, Math.min(1,
                (target-d[seg-1])/span)) : 0;
            Point a = path.get(seg-1), b = path.get(seg);
            samples.add(new Point(a.x()+(b.x()-a.x())*t,
                a.y()+(b.y()-a.y())*t));
        }
        return samples;
    }
}
