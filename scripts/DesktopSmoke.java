import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.json.JSONObject;
import pl.vistula.checker.Demo;
import pl.vistula.checker.DesktopVault;
import pl.vistula.checker.DesktopInstanceLock;
import pl.vistula.checker.DesktopKeyValueStore;

/** Exercises a packaged app against a disposable fixture account, never a user's data directory. */
public class DesktopSmoke {
    static void shortcut(Robot robot, int key) {
        robot.keyPress(KeyEvent.VK_CONTROL); robot.keyPress(key);
        robot.keyRelease(key); robot.keyRelease(KeyEvent.VK_CONTROL); robot.delay(1000);
    }
    static BufferedImage shot(Robot robot, Path folder, String name) throws Exception {
        BufferedImage image = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
        if (name.startsWith("personal-")) {
            // The prototype's account caption is 13 px. Enlarge its region for OCR;
            // retain the original full-resolution capture for visual diagnostics.
            int left = image.getWidth(), top = image.getHeight();
            // xvfb has a black desktop. Locate the application surface before cropping
            // so neither the desktop border nor navigation pills affect segmentation.
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ffffff) != 0) { left = Math.min(left, x); top = Math.min(top, y); }
            }
            if (left == image.getWidth()) throw new AssertionError("No visible desktop application surface");
            int x = left + 16, y = top + 24;
            int width = Math.min(280, image.getWidth() - x), height = Math.min(60, image.getHeight() - y);
            BufferedImage header = new BufferedImage(width * 4, height * 4, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = header.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(image.getSubimage(x, y, width, height), 0, 0, width * 4, height * 4, null);
            } finally { graphics.dispose(); }
            ImageIO.write(header, "png", folder.resolve(name + "-header.png").toFile());
        }
        return image;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Path allowed = Path.of("work/desktop-smoke").toAbsolutePath().normalize();
        if (!root.startsWith(allowed)) throw new IllegalArgumentException("A disposable fixture directory is required");
        Path data = root.resolve(System.getProperty("os.name").startsWith("Windows") ? "profile/VistulaChecker" : "profile/vistula-checker");
        if (args[0].equals("seed")) {
            JSONObject fixture = Demo.state(false).put("token", "fixture-personal-token").put("enabled", false)
                .put("user", "Personal desktop fixture").put("accountSession", "fixture-session");
            fixture.remove("profileTranslations");
            new DesktopVault(data.toFile()).write(fixture);
            DesktopKeyValueStore prefs = new DesktopKeyValueStore(data.resolve("preferences.json").toFile());
            prefs.putString("ui_language", "en"); prefs.putString("ui_theme_mode", args.length > 2 ? args[2] : "light");
            Files.write(root.resolve("original-state.bin"), Files.readAllBytes(data.resolve("state.bin")));
            return;
        }
        if (args[0].equals("probe")) {
            try (DesktopInstanceLock ignored = new DesktopInstanceLock(data.toFile())) {
                throw new AssertionError("The packaged app did not hold the fixture instance lock");
            } catch (DesktopInstanceLock.AlreadyRunning expected) { }
            if (!Arrays.equals(Files.readAllBytes(root.resolve("original-state.bin")), Files.readAllBytes(data.resolve("state.bin"))))
                throw new AssertionError("Launch modified the saved fixture account");
            return;
        }
        Robot robot = new Robot(); robot.delay(7000);
        BufferedImage before = shot(robot, root, "personal-before");
        try (DesktopInstanceLock ignored = new DesktopInstanceLock(data.toFile())) {
            throw new AssertionError("The packaged app did not hold the account's instance lock");
        } catch (DesktopInstanceLock.AlreadyRunning expected) { }

        shortcut(robot, KeyEvent.VK_D);
        robot.keyPress(KeyEvent.VK_F5); robot.keyRelease(KeyEvent.VK_F5); robot.delay(1500);
        BufferedImage demo = shot(robot, root, "demo");
        long changed = 0;
        for (int y = 0; y < before.getHeight(); y += 2) for (int x = 0; x < before.getWidth(); x += 2)
            if (before.getRGB(x, y) != demo.getRGB(x, y)) changed++;
        if (changed < 100) throw new AssertionError("Demo did not visibly change the application");
        shortcut(robot, KeyEvent.VK_2); shot(robot, root, "schedule");
        shortcut(robot, KeyEvent.VK_1); shot(robot, root, "home-between"); shortcut(robot, KeyEvent.VK_2); shot(robot, root, "schedule-repeat");
        shortcut(robot, KeyEvent.VK_5); shot(robot, root, "more");
        shortcut(robot, KeyEvent.VK_D); shot(robot, root, "personal-after");
        if (!Arrays.equals(Files.readAllBytes(root.resolve("original-state.bin")), Files.readAllBytes(data.resolve("state.bin"))))
            throw new AssertionError("Demo or refresh modified the personal account vault");
        System.out.println("Desktop window, demo refresh and keyboard navigation passed; personal vault unchanged");
    }
}
