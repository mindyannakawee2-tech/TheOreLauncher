import com.google.gson.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.List;

public class TheOreLauncher extends JFrame {
    private static final String OWNER = "mindyannakawee2-tech";
    private static final String REPO = "TheOreReleases";
    private static final String API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases";
    private static final Path VERSIONS = Path.of(System.getProperty("user.home"), ".local", "share", "TheOre", "versions");
    private static final Color BG = new Color(22, 27, 35), PANEL = new Color(32, 40, 51);
    private static final Color GOLD = new Color(238, 192, 108), WHITE = new Color(235, 240, 244);
    private final JComboBox<Release> versions = new JComboBox<>();
    private final JButton play = new JButton("PLAY / INSTALL"), refresh = new JButton("Refresh releases");
    private final JLabel status = new JLabel("Checking releases...");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(12)).build();
    private record Asset(String name, URI uri, long size) {}
    private record Release(String tag, String label, String notes, Asset jar, Asset checksum) {
        @Override public String toString() { return label + "  (" + tag + ")"; }
    }
    // Classic launcher theme: painted using Swing, no generated or external imagery.
    private static final Color TOP = new Color(26, 28, 27), BAR = new Color(35, 36, 35);
    private static final Color INK = new Color(221, 225, 223), GREEN = new Color(57, 153, 45);
    private final JPanel content = new JPanel(new CardLayout());
    private final JButton newsTab = tab("NEWS"), skinsTab = tab("SKINS"),
        settingsTab = tab("SETTINGS"), launchTab = tab("LAUNCH OPTIONS"), installsTab = tab("INSTALLATIONS");
    private final JButton[] tabs = {newsTab, skinsTab, settingsTab, launchTab, installsTab};
    private final JLabel newsTitle = label("THEORE NEWS", 23, true, WHITE);
    private final JTextArea newsBody = new JTextArea();
    private final JLabel newsCounter = label("", 12, false, INK);
    private final JButton previousNews = new JButton("<"), nextNews = new JButton(">");
    private List<Release> newsReleases = new ArrayList<>();
    private int currentNews = 0;

    private static JButton tab(String name) {
        JButton b = new JButton(name);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setBorder(new EmptyBorder(12, 15, 12, 15));
        b.setBackground(TOP); b.setForeground(INK);
        b.setFocusPainted(false); b.setOpaque(true); b.setBorderPainted(false);
        return b;
    }
    private static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }
    private static void solidButton(JButton b, Color color, int font) {
        b.setBackground(color); b.setForeground(WHITE); b.setOpaque(true);
        b.setFocusPainted(false); b.setFont(new Font("SansSerif", Font.BOLD, font));
        b.setBorder(BorderFactory.createLineBorder(color.darker(), 2));
    }
    public TheOreLauncher() {
        super("TheORE Launcher");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(950, 670);
        setMinimumSize(new Dimension(790, 550));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(TOP);
        header.add(new PixelHeader(), BorderLayout.NORTH);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nav.setOpaque(false);
        for (JButton t : tabs) nav.add(t);
        header.add(nav, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        content.setBackground(PANEL);
        content.add(newsPage(), "NEWS");
        content.add(simplePage("SKINS", "Player skin customization is planned for a future version."), "SKINS");
        content.add(settingsPage(), "SETTINGS");
        content.add(simplePage("LAUNCH OPTIONS", "Choose an installed release below. Game runtime: Java 25."), "LAUNCH OPTIONS");
        content.add(installationsPage(), "INSTALLATIONS");
        add(content, BorderLayout.CENTER);
        for (JButton t : tabs) t.addActionListener(e -> showPage(t.getText()));
        showPage("NEWS");

        JPanel footer = new JPanel(new BorderLayout(0, 9));
        footer.setBackground(BAR);
        footer.setBorder(new EmptyBorder(12, 18, 10, 18));
        JPanel controls = new JPanel(new BorderLayout(10, 0));
        controls.setOpaque(false);
        versions.setPreferredSize(new Dimension(270, 55));
        versions.setFont(new Font("SansSerif", Font.BOLD, 13));
        versions.setBackground(new Color(54, 56, 55));
        versions.setForeground(WHITE);
        controls.add(versions, BorderLayout.WEST);
        play.setText("PLAY");
        play.setPreferredSize(new Dimension(380, 55));
        solidButton(play, GREEN, 25);
        controls.add(play, BorderLayout.CENTER);
        refresh.setText("REFRESH");
        refresh.setPreferredSize(new Dimension(95, 55));
        solidButton(refresh, new Color(73, 75, 74), 12);
        controls.add(refresh, BorderLayout.EAST);
        footer.add(controls, BorderLayout.NORTH);
        progress.setStringPainted(true);
        progress.setVisible(false);
        footer.add(progress, BorderLayout.CENTER);
        status.setForeground(new Color(187, 195, 189));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        play.addActionListener(e -> playSelected());
        refresh.addActionListener(e -> loadReleases());
        versions.addActionListener(e -> updatePlayLabel());
        previousNews.addActionListener(e -> {currentNews--; updateNews();});
        nextNews.addActionListener(e -> {currentNews++; updateNews();});
        loadReleases();
    }
    private static class PixelHeader extends JPanel {
        PixelHeader() {setPreferredSize(new Dimension(900, 91)); setBackground(TOP);}
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D)graphics.create();
            int w=getWidth(), h=getHeight();
            g.setColor(new Color(31, 33, 32));g.fillRect(0,0,w,h);
            // Subtle pixel / ore squares, intentionally rendered as shapes.
            for (int i=0;i<w/32+2;i++) {
                for(int j=0;j<3;j++) {
                    int code=(i*47+j*29)%13;
                    g.setColor(code<4?new Color(36,39,37):new Color(27,29,28));
                    g.fillRect(i*32+(j%2)*9,j*29,28,27);
                }
            }
            g.setColor(new Color(18,20,19));g.fillRect(0,h-5,w,5);
            g.setFont(new Font("SansSerif",Font.BOLD,38));
            g.setColor(new Color(5,8,7));g.drawString("TheORE",23,60);
            g.setColor(new Color(235,237,229));g.drawString("TheORE",21,57);
            g.setFont(new Font("SansSerif",Font.PLAIN,12));
            g.setColor(new Color(158,167,155));g.drawString("OFFICIAL LAUNCHER",208,55);
            g.dispose();
        }
    }
    private JPanel newsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBackground(new Color(220, 221, 216));
        page.setBorder(new EmptyBorder(18, 22, 15, 22));
        JPanel stage = new JPanel(new BorderLayout(0, 0));
        stage.setBackground(new Color(50, 70, 61));
        stage.setBorder(BorderFactory.createLineBorder(new Color(75,86,74),2));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);top.setBorder(new EmptyBorder(15,18,8,18));
        top.add(newsTitle, BorderLayout.WEST);
        top.add(newsCounter, BorderLayout.EAST);
        stage.add(top,BorderLayout.NORTH);
        newsBody.setLineWrap(true);newsBody.setWrapStyleWord(true);
        newsBody.setEditable(false);newsBody.setFocusable(false);
        newsBody.setBackground(new Color(50, 70, 61));
        newsBody.setForeground(new Color(234, 239, 227));
        newsBody.setFont(new Font("SansSerif",Font.PLAIN,16));
        newsBody.setBorder(new EmptyBorder(18,25,18,25));
        stage.add(new JScrollPane(newsBody,ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER),BorderLayout.CENTER);
        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);navigation.setBorder(new EmptyBorder(5,12,12,12));
        solidButton(previousNews,new Color(38,48,43),19);
        solidButton(nextNews,new Color(38,48,43),19);
        navigation.add(previousNews,BorderLayout.WEST);
        navigation.add(nextNews,BorderLayout.EAST);
        stage.add(navigation,BorderLayout.SOUTH);
        page.add(stage,BorderLayout.CENTER);
        JLabel caption = label("LATEST UPDATES  /  GitHub: " + OWNER + "/" + REPO,12,true,new Color(48,53,50));
        page.add(caption,BorderLayout.SOUTH);
        newsBody.setText("Welcome to TheORE!\n\nRelease announcements will appear here when GitHub finishes loading.");
        return page;
    }
    private void updateNews() {
        if(newsReleases.isEmpty()) {
            newsTitle.setText("WELCOME TO TheORE");
            newsBody.setText("No installable releases found.\n\nPublish a game JAR and matching .sha256 file in TheOreReleases to get started.");
            newsCounter.setText("NO NEWS");
            previousNews.setEnabled(false);nextNews.setEnabled(false);
            return;
        }
        currentNews=Math.floorMod(currentNews,newsReleases.size());
        Release r=newsReleases.get(currentNews);
        newsTitle.setText(r.label());
        newsBody.setText("Release " + r.tag() + "\n\n" + (r.notes().isBlank()?"This version is available for download and play.":r.notes()));
        newsBody.setCaretPosition(0);
        newsCounter.setText((currentNews+1)+" / "+newsReleases.size());
        previousNews.setEnabled(newsReleases.size()>1);nextNews.setEnabled(newsReleases.size()>1);
    }
    private JPanel simplePage(String heading, String body) {
        JPanel panel=new JPanel(new BorderLayout(0,12));
        panel.setBackground(new Color(220,221,216));
        panel.setBorder(new EmptyBorder(30,30,30,30));
        panel.add(label(heading,27,true,new Color(43,49,44)),BorderLayout.NORTH);
        JTextArea text=new JTextArea(body);
        text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        text.setFont(new Font("SansSerif",Font.PLAIN,16));
        text.setBackground(new Color(220,221,216));
        text.setForeground(new Color(44,53,47));
        panel.add(text,BorderLayout.CENTER);
        return panel;
    }
    private JPanel installationsPage() {
        return simplePage("INSTALLATIONS", "Installed game versions are stored at:\n" + VERSIONS +
            "\n\nSelect a release from the bottom bar to install or launch it.");
    }
    private JPanel settingsPage() {
        return simplePage("SETTINGS", "Game release source (exclusive):\nhttps://github.com/" + OWNER + "/" + REPO +
            "\n\nTheORE Launcher v0.3\nUpdates are checked when opening the launcher or pressing REFRESH.");
    }
    private void showPage(String page) {
        ((CardLayout)content.getLayout()).show(content,page);
        for(JButton b:tabs) {b.setBackground(TOP);b.setForeground(INK);}
        for(JButton b:tabs) if(b.getText().equals(page)) {
            b.setBackground(new Color(68,144,56));b.setForeground(Color.WHITE);
        }
    }
    private void busy(boolean isBusy, String message) {
        play.setEnabled(!isBusy && versions.getSelectedItem()!=null);
        refresh.setEnabled(!isBusy); versions.setEnabled(!isBusy);
        status.setText(message);
    }
    private void updatePlayLabel() {
        Release r=(Release)versions.getSelectedItem();
        if(r==null){play.setEnabled(false);return;}
        play.setEnabled(true);
        play.setText(Files.isRegularFile(installedPath(r)) ? "PLAY" : "INSTALL & PLAY");
    }
    private Path installedPath(Release r) { return VERSIONS.resolve(r.tag()).resolve("TheOre.jar"); }
    private HttpResponse<String> getText(URI url) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(25))
            .header("User-Agent","TheORELauncher/0.3").header("Accept","application/vnd.github+json").GET().build();
        HttpResponse<String> resp=http.send(req,HttpResponse.BodyHandlers.ofString());
        if(resp.statusCode()!=200) throw new IOException("HTTP " + resp.statusCode() + " from " + url.getHost());
        return resp;
    }
    private void loadReleases() {
        busy(true,"Fetching versions exclusively from GitHub: " + OWNER + "/" + REPO);
        new SwingWorker<List<Release>,Void>() {
            @Override protected List<Release> doInBackground() throws Exception {
                JsonArray arr=JsonParser.parseString(getText(URI.create(API)).body()).getAsJsonArray();
                List<Release> results=new ArrayList<>();
                for(JsonElement item:arr){
                    JsonObject obj=item.getAsJsonObject();
                    if(obj.has("draft") && obj.get("draft").getAsBoolean())continue;
                    String tag=obj.get("tag_name").getAsString();
                    if(!tag.matches("[A-Za-z0-9._-]{1,80}"))continue;
                    String title=obj.get("name").isJsonNull()?tag:obj.get("name").getAsString();
                    String notes=obj.has("body") && !obj.get("body").isJsonNull()?obj.get("body").getAsString():"";
                    Asset jar=null, sha=null;
                    for(JsonElement a:obj.getAsJsonArray("assets")){
                        JsonObject asset=a.getAsJsonObject();
                        String name=asset.get("name").getAsString();
                        URI uri=URI.create(asset.get("browser_download_url").getAsString());
                        String prefix="https://github.com/"+OWNER+"/"+REPO+"/releases/download/";
                        if(!uri.toString().startsWith(prefix))continue;
                        Asset candidate=new Asset(name,uri,asset.get("size").getAsLong());
                        if(name.matches("TheOre-[A-Za-z0-9._-]+\\.jar"))jar=candidate;
                        if(name.equals("TheOre-"+tag+".jar.sha256"))sha=candidate;
                    }
                    if(jar!=null && sha!=null && jar.name().equals("TheOre-"+tag+".jar"))results.add(new Release(tag,title,notes,jar,sha));
                }
                return results;
            }
            @Override protected void done(){
                try {
                    List<Release> releases=get();
                    versions.removeAllItems(); for(Release r:releases)versions.addItem(r);
                    newsReleases=releases;currentNews=0;updateNews();
                    busy(false,releases.isEmpty()?"No installable releases yet. Publish TheOre-vX.jar + matching .sha256 asset.":releases.size()+" verified-format release(s) available.");
                    updatePlayLabel();
                } catch(Exception ex) { versions.removeAllItems();newsTitle.setText("OFFLINE");newsBody.setText("Could not load GitHub release information.\n\n"+message(ex));busy(false,"Unable to load releases: "+message(ex));updatePlayLabel(); }
            }
        }.execute();
    }
    private void playSelected() {
        Release r=(Release)versions.getSelectedItem();if(r==null)return;
        Path installed=installedPath(r);
        if(Files.isRegularFile(installed)){startGame(installed);return;}
        busy(true,"Downloading " + r.tag() + "...");progress.setValue(0);progress.setVisible(true);
        new SwingWorker<Path,Integer>() {
            @Override protected Path doInBackground() throws Exception {
                Files.createDirectories(installed.getParent());
                String checksum=getText(r.checksum().uri()).body().trim();
                String expected=checksum.split("\\s+")[0];
                if(!expected.matches("(?i)[0-9a-f]{64}"))throw new IOException("Invalid SHA-256 checksum file");
                Path temp=Files.createTempFile(installed.getParent(),"download-",".part");
                try {
                    HttpRequest request=HttpRequest.newBuilder(r.jar().uri()).timeout(Duration.ofMinutes(5))
                        .header("User-Agent","TheORELauncher/0.3").GET().build();
                    HttpResponse<InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
                    if(response.statusCode()!=200)throw new IOException("Download failed: HTTP "+response.statusCode());
                    MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    long total=0;
                    try(InputStream in=response.body();OutputStream out=Files.newOutputStream(temp)){
                        byte[] buffer=new byte[65536];int n;
                        while((n=in.read(buffer))!=-1){
                            out.write(buffer,0,n); digest.update(buffer,0,n);total+=n;
                            if(r.jar().size()>0)publish((int)Math.min(100,total*100/r.jar().size()));
                        }
                    }
                    String actual=HexFormat.of().formatHex(digest.digest());
                    if(!expected.equalsIgnoreCase(actual))throw new SecurityException("SHA-256 mismatch: download not installed");
                    Files.move(temp,installed,StandardCopyOption.REPLACE_EXISTING);
                    return installed;
                }finally{Files.deleteIfExists(temp);}
            }
            @Override protected void process(List<Integer> values){progress.setValue(values.getLast());}
            @Override protected void done(){
                progress.setVisible(false);
                try {Path path=get();busy(false,"SHA-256 verified. Installed " + r.tag());updatePlayLabel();startGame(path);}
                catch(Exception ex){busy(false,"Install failed: "+message(ex));updatePlayLabel();}
            }
        }.execute();
    }
    private void startGame(Path jar) {
        try {
            Path javaExe=Path.of(System.getProperty("java.home"),"bin","java");
            new ProcessBuilder(javaExe.toString(),"-jar",jar.toAbsolutePath().toString())
                .directory(jar.getParent().toFile()).inheritIO().start();
            status.setText("Game launched: " + jar.getParent().getFileName());
        }catch(IOException ex){status.setText("Could not launch game: "+ex.getMessage());}
    }
    private static String message(Exception ex){
        Throwable t=ex;while(t.getCause()!=null)t=t.getCause();return String.valueOf(t.getMessage());
    }
    public static void main(String[] args){
        try {UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception ignored){}
        SwingUtilities.invokeLater(()->new TheOreLauncher().setVisible(true));
    }
}
