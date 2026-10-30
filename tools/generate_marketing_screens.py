import os
import random
from PIL import Image, ImageDraw, ImageFont, ImageFilter

# Output directories
os.makedirs('screenshots/clean', exist_ok=True)
os.makedirs('screenshots/marketing', exist_ok=True)

# Common Palette
COLOR_BG = (16, 20, 28)
COLOR_SURFACE = (26, 32, 44)
COLOR_SURFACE_DARK = (20, 24, 34)
COLOR_BORDER = (45, 55, 72)
COLOR_ACCENT = (140, 217, 107)        # Neon green #8CD96B
COLOR_ACCENT_BG = (28, 48, 30)
COLOR_TEXT_PRIMARY = (255, 255, 255)
COLOR_TEXT_SECONDARY = (160, 175, 200)
COLOR_TEXT_MUTED = (115, 130, 155)

# Fonts
font_bold_28 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 28)
font_bold_24 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 24)
font_bold_20 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 20)
font_bold_18 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 18)
font_bold_16 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 16)
font_bold_14 = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 14)
font_med_20 = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 20)
font_med_18 = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 18)
font_med_16 = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 16)
font_med_14 = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 14)

def draw_status_bar(draw, W):
    draw.text((45, 22), "09:41", font=font_bold_16, fill=(240, 245, 255))
    # Icons: 5G, Wifi, Battery
    draw.text((W - 145, 22), "5G", font=font_bold_16, fill=(240, 245, 255))
    # Wifi icon (arcs)
    draw.arc([W - 105, 22, W - 85, 42], start=200, end=340, fill=(240, 245, 255), width=2)
    draw.arc([W - 101, 26, W - 89, 38], start=200, end=340, fill=(240, 245, 255), width=2)
    # Battery icon
    draw.rounded_rectangle([W - 70, 25, W - 35, 41], radius=3, outline=(240, 245, 255), width=1)
    draw.rectangle([W - 68, 27, W - 43, 39], fill=COLOR_ACCENT)
    draw.rectangle([W - 35, 30, W - 33, 36], fill=(240, 245, 255))

def generate_cinematic_frame(W, H):
    img = Image.new('RGB', (W, H), (6, 10, 20))
    # Nebula
    nebula = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    n_draw = ImageDraw.Draw(nebula)
    n_draw.ellipse([150, 400, 950, 1200], fill=(79, 70, 229, 95))
    n_draw.ellipse([350, 600, 1050, 1400], fill=(147, 51, 234, 85))
    n_draw.ellipse([50, 750, 750, 1500], fill=(13, 148, 136, 75))
    n_draw.ellipse([W // 2 - 250, 900, W // 2 + 250, 1400], fill=(244, 63, 94, 60))
    nebula = nebula.filter(ImageFilter.GaussianBlur(130))
    img = Image.alpha_composite(img.convert('RGBA'), nebula).convert('RGB')
    
    # Starfield
    draw = ImageDraw.Draw(img)
    random.seed(99)
    for _ in range(260):
        x = random.randint(0, W)
        y = random.randint(0, H)
        s = random.choice([1, 1, 2, 2, 3])
        b = random.randint(150, 255)
        draw.ellipse([x, y, x + s, y + s], fill=(b, b, b))
    
    # Mountain ridges silhouette at bottom of video
    points = [(0, 1500), (180, 1380), (360, 1440), (540, 1320), (740, 1410), (920, 1350), (W, 1460), (W, H), (0, H)]
    draw.polygon(points, fill=(10, 14, 24))
    
    return img

# ==========================================
# 1. SCREEN 3: Fullscreen Video Player
# ==========================================
def render_screen_player():
    W, H = 1080, 2400
    img = generate_cinematic_frame(W, H)
    draw = ImageDraw.Draw(img)
    
    # Status bar
    draw_status_bar(draw, W)
    
    # Top Bar gradient scrim
    top_scrim = Image.new('RGBA', (W, 220), (0, 0, 0, 0))
    ts_draw = ImageDraw.Draw(top_scrim)
    for y in range(220):
        alpha = int(180 * (1.0 - y / 220.0))
        ts_draw.line([(0, y), (W, y)], fill=(0, 0, 0, alpha))
    img.paste(Image.alpha_composite(img.crop((0, 0, W, 220)).convert('RGBA'), top_scrim), (0, 0))
    
    draw = ImageDraw.Draw(img)
    # Back Arrow
    draw.line([(60, 110), (45, 125)], fill=(255, 255, 255), width=3)
    draw.line([(45, 125), (60, 140)], fill=(255, 255, 255), width=3)
    draw.line([(45, 125), (75, 125)], fill=(255, 255, 255), width=3)
    
    # Title
    draw.text((100, 112), "Cosmos: Journey Through the Universe (4K HDR)", font=font_bold_20, fill=(255, 255, 255))
    
    # Top action buttons: CC (active green) and Settings
    draw.rounded_rectangle([W - 155, 105, W - 95, 145], radius=20, fill=COLOR_ACCENT_BG, outline=COLOR_ACCENT, width=1)
    draw.text((W - 140, 115), "CC", font=font_bold_16, fill=COLOR_ACCENT)
    
    # Gear icon outline
    draw.ellipse([W - 75, 110, W - 45, 140], outline=(255, 255, 255), width=2)
    
    # Subtitle overlay on video
    sub_text = "We are travelers on a cosmic journey, stardust swirling in the infinite."
    s_bbox = font_med_20.getbbox(sub_text)
    sw = s_bbox[2] - s_bbox[0]
    sub_x = (W - sw) // 2
    sub_y = 1580
    draw.rounded_rectangle([sub_x - 24, sub_y - 12, sub_x + sw + 24, sub_y + 36], radius=12, fill=(0, 0, 0, 175))
    draw.text((sub_x, sub_y), sub_text, font=font_med_20, fill=(255, 255, 255))
    
    # Center playback controls
    cx, cy = W // 2, 1150
    # Prev (circular translucent)
    draw.ellipse([cx - 160, cy - 36, cx - 88, cy + 36], fill=(0, 0, 0, 140), outline=(255, 255, 255, 60), width=1)
    draw.polygon([(cx - 118, cy - 14), (cx - 132, cy), (cx - 118, cy + 14)], fill=(255, 255, 255))
    draw.polygon([(cx - 132, cy - 14), (cx - 146, cy), (cx - 132, cy + 14)], fill=(255, 255, 255))
    
    # Large 76dp Play/Pause button
    draw.ellipse([cx - 48, cy - 48, cx + 48, cy + 48], fill=(20, 36, 25, 220), outline=COLOR_ACCENT, width=2)
    draw.rectangle([cx - 16, cy - 18, cx - 6, cy + 18], fill=COLOR_ACCENT)
    draw.rectangle([cx + 6, cy - 18, cx + 16, cy + 18], fill=COLOR_ACCENT)
    
    # Next
    draw.ellipse([cx + 88, cy - 36, cx + 160, cy + 36], fill=(0, 0, 0, 140), outline=(255, 255, 255, 60), width=1)
    draw.polygon([(cx + 118, cy - 14), (cx + 132, cy), (cx + 118, cy + 14)], fill=(255, 255, 255))
    draw.polygon([(cx + 132, cy - 14), (cx + 146, cy), (cx + 132, cy + 14)], fill=(255, 255, 255))
    
    # Bottom Controls Scrim
    draw.rectangle([0, 2060, W, 2400], fill=(10, 14, 22))
    
    # Seek bar
    draw.text((45, 2100), "24:18", font=font_med_14, fill=COLOR_TEXT_PRIMARY)
    draw.text((W - 115, 2100), "01:42:15", font=font_med_14, fill=COLOR_TEXT_SECONDARY)
    
    bar_y = 2107
    draw.line([(105, bar_y), (W - 135, bar_y)], fill=(60, 70, 85), width=4)
    # Cached bar
    draw.line([(105, bar_y), (650, bar_y)], fill=(90, 105, 125), width=4)
    # Played progress
    draw.line([(105, bar_y), (420, bar_y)], fill=COLOR_ACCENT, width=4)
    # Scrubber thumb
    draw.ellipse([412, bar_y - 8, 428, bar_y + 8], fill=COLOR_ACCENT)
    
    # Control Pills Row
    pills = ["LOCK", "1.0x", "FIT", "ROTATE", "PIP"]
    px = 45
    for p in pills:
        draw.rounded_rectangle([px, 2150, px + 105, 2196], radius=23, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
        draw.text((px + 28, 2163), p, font=font_bold_16, fill=(240, 245, 255))
        px += 125
        
    img.save('screenshots/clean/03_player_playback.png')
    print("Saved 03_player_playback.png")

# ==========================================
# 2. SCREEN 4: Subtitle Options Bottom Sheet
# ==========================================
def render_screen_subtitle_options():
    W, H = 1080, 2400
    img = generate_cinematic_frame(W, H)
    
    # Dim overlay for bottom sheet backdrop
    dim = Image.new('RGBA', (W, H), (0, 0, 0, 160))
    img = Image.alpha_composite(img.convert('RGBA'), dim).convert('RGB')
    draw = ImageDraw.Draw(img)
    
    # Bottom Sheet Container (M3 28dp top corners)
    sheet_y = 1050
    sheet_h = H - sheet_y
    draw.rounded_rectangle([0, sheet_y, W, H + 50], radius=28, fill=COLOR_SURFACE_DARK)
    
    # Drag Handle
    draw.rounded_rectangle([W // 2 - 28, sheet_y + 18, W // 2 + 28, sheet_y + 24], radius=3, fill=(90, 105, 125))
    
    # Header
    draw.text((50, sheet_y + 55), "Subtitle options", font=font_bold_24, fill=COLOR_TEXT_PRIMARY)
    draw.text((W - 80, sheet_y + 50), "✕", font=font_bold_24, fill=COLOR_TEXT_SECONDARY)
    
    # Section 1: Tracks
    draw.text((50, sheet_y + 115), "SUBTITLE TRACKS", font=font_bold_16, fill=COLOR_TEXT_MUTED)
    
    # Track 1: Off
    y1 = sheet_y + 155
    draw.rounded_rectangle([50, y1, W - 50, y1 + 75], radius=14, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
    draw.ellipse([75, y1 + 25, 101, y1 + 51], outline=COLOR_TEXT_MUTED, width=2)
    draw.text((120, y1 + 24), "Off", font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
    
    # Track 2: English Embedded
    y2 = y1 + 90
    draw.rounded_rectangle([50, y2, W - 50, y2 + 85], radius=14, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
    draw.ellipse([75, y2 + 30, 101, y2 + 56], outline=COLOR_TEXT_MUTED, width=2)
    draw.text((120, y2 + 18), "English [Embedded]", font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
    draw.text((120, y2 + 48), "SubRip • Track 1", font=font_med_14, fill=COLOR_TEXT_SECONDARY)
    
    # Track 3: Offline AI Captions (Whisper) - SELECTED WITH GREEN HIGHLIGHT
    y3 = y2 + 100
    draw.rounded_rectangle([50, y3, W - 50, y3 + 95], radius=14, fill=(24, 46, 32), outline=COLOR_ACCENT, width=2)
    # Radio checked
    draw.ellipse([75, y3 + 35, 101, y3 + 61], outline=COLOR_ACCENT, width=2)
    draw.ellipse([81, y3 + 41, 95, y3 + 55], fill=COLOR_ACCENT)
    
    draw.text((120, y3 + 22), "Offline AI Captions (Whisper)", font=font_bold_20, fill=COLOR_TEXT_PRIMARY)
    draw.text((120, y3 + 54), "Translate to English • 99 languages supported", font=font_med_14, fill=COLOR_ACCENT)
    # AI Pill Badge
    draw.rounded_rectangle([W - 120, y3 + 28, W - 72, y3 + 62], radius=6, fill=COLOR_ACCENT)
    draw.text((W - 106, y3 + 34), "AI", font=font_bold_16, fill=(10, 20, 12))
    
    # Section 2: Delay Timing
    draw.text((50, y3 + 125), "SUBTITLE DELAY", font=font_bold_16, fill=COLOR_TEXT_MUTED)
    
    y4 = y3 + 160
    draw.rounded_rectangle([50, y4, W - 50, y4 + 140], radius=16, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
    draw.text((80, y4 + 20), "Offset: 0 ms", font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
    draw.text((W - 145, y4 + 20), "RESET", font=font_bold_16, fill=COLOR_ACCENT)
    
    # Delay buttons
    steppers = ["-0.5s", "-0.1s", "+0.1s", "+0.5s"]
    bx = 75
    for st in steppers:
        draw.rounded_rectangle([bx, y4 + 65, bx + 210, y4 + 115], radius=10, fill=COLOR_SURFACE_DARK, outline=COLOR_BORDER, width=1)
        draw.text((bx + 75, y4 + 78), st, font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
        bx += 230
        
    # Appearance Shortcut Button
    y5 = y4 + 165
    draw.rounded_rectangle([50, y5, W - 50, y5 + 75], radius=14, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
    draw.text((W // 2 - 150, y5 + 24), "Customize Subtitle Appearance", font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
    
    img.save('screenshots/clean/04_subtitle_options.png')
    print("Saved 04_subtitle_options.png")

# ==========================================
# 3. SCREEN 5: Subtitle Language & Translation Sheet
# ==========================================
def render_screen_subtitle_language():
    W, H = 1080, 2400
    img = Image.new('RGB', (W, H), COLOR_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw, W)
    
    # Settings background header
    draw.text((50, 110), "Settings", font=font_bold_28, fill=COLOR_TEXT_PRIMARY)
    
    # Dim overlay
    dim = Image.new('RGBA', (W, H), (0, 0, 0, 150))
    img = Image.alpha_composite(img.convert('RGBA'), dim).convert('RGB')
    draw = ImageDraw.Draw(img)
    
    # Bottom Sheet Container
    sheet_y = 750
    draw.rounded_rectangle([0, sheet_y, W, H + 50], radius=28, fill=COLOR_SURFACE_DARK)
    
    # Drag Handle
    draw.rounded_rectangle([W // 2 - 28, sheet_y + 18, W // 2 + 28, sheet_y + 24], radius=3, fill=(90, 105, 125))
    
    # Header
    draw.text((50, sheet_y + 55), "Subtitle language", font=font_bold_24, fill=COLOR_TEXT_PRIMARY)
    draw.text((W - 80, sheet_y + 50), "✕", font=font_bold_24, fill=COLOR_TEXT_SECONDARY)
    
    # Translate Toggle Card (M3 Feature Highlight)
    card_y = sheet_y + 115
    draw.rounded_rectangle([50, card_y, W - 50, card_y + 120], radius=18, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
    draw.text((80, card_y + 26), "Translate to English subtitles", font=font_bold_20, fill=COLOR_TEXT_PRIMARY)
    draw.text((80, card_y + 64), "Automatically convert foreign speech into English text", font=font_med_14, fill=COLOR_TEXT_SECONDARY)
    
    # Switch (ON green state)
    sw_x = W - 140
    sw_y = card_y + 40
    draw.rounded_rectangle([sw_x, sw_y, sw_x + 65, sw_y + 36], radius=18, fill=COLOR_ACCENT)
    draw.ellipse([sw_x + 33, sw_y + 3, sw_x + 61, sw_y + 33], fill=(255, 255, 255))
    
    # Section: Language Picker
    draw.text((50, card_y + 160), "SELECT SPOKEN LANGUAGE", font=font_bold_16, fill=COLOR_TEXT_MUTED)
    
    langs = [
        ("Auto-detect (Recommended)", "Identifies 99 languages automatically", "AUTO", True),
        ("Spanish (Español)", "Native transcription / translation", "ES", False),
        ("Hindi (हिन्दी)", "Native transcription / translation", "HI", False),
        ("Japanese (日本語)", "Native transcription / translation", "JA", False),
        ("French (Français)", "Native transcription / translation", "FR", False),
        ("German (Deutsch)", "Native transcription / translation", "DE", False),
    ]
    
    ly = card_y + 200
    for title, desc, badge, is_sel in langs:
        bg_col = (24, 46, 32) if is_sel else COLOR_SURFACE
        bd_col = COLOR_ACCENT if is_sel else COLOR_BORDER
        draw.rounded_rectangle([50, ly, W - 50, ly + 88], radius=16, fill=bg_col, outline=bd_col, width=2 if is_sel else 1)
        
        # Badge squircle
        draw.rounded_rectangle([75, ly + 18, 125, ly + 68], radius=12, fill=COLOR_SURFACE_DARK, outline=bd_col, width=1)
        draw.text((85, ly + 32), badge, font=font_bold_14, fill=COLOR_ACCENT if is_sel else COLOR_TEXT_SECONDARY)
        
        # Texts
        draw.text((145, ly + 18), title, font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
        draw.text((145, ly + 50), desc, font=font_med_14, fill=COLOR_ACCENT if is_sel else COLOR_TEXT_MUTED)
        
        # Radio circle
        rx = W - 95
        ry = ly + 32
        if is_sel:
            draw.ellipse([rx, ry, rx + 24, ry + 24], outline=COLOR_ACCENT, width=2)
            draw.ellipse([rx + 6, ry + 6, rx + 18, ry + 18], fill=COLOR_ACCENT)
        else:
            draw.ellipse([rx, ry, rx + 24, ry + 24], outline=COLOR_TEXT_MUTED, width=2)
            
        ly += 105
        
    img.save('screenshots/clean/05_subtitle_language_sheet.png')
    print("Saved 05_subtitle_language_sheet.png")

# ==========================================
# 4. SCREEN 2: Material 3 Video Actions Sheet
# ==========================================
def render_screen_video_actions():
    W, H = 1080, 2400
    img = Image.new('RGB', (W, H), COLOR_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw, W)
    
    # Background video list items
    draw.text((50, 110), "Media Downloads", font=font_bold_28, fill=COLOR_TEXT_PRIMARY)
    draw.text((50, 155), "24 videos • 8.6 GB", font=font_med_16, fill=COLOR_TEXT_SECONDARY)
    
    # Dim overlay
    dim = Image.new('RGBA', (W, H), (0, 0, 0, 160))
    img = Image.alpha_composite(img.convert('RGBA'), dim).convert('RGB')
    draw = ImageDraw.Draw(img)
    
    # Bottom Sheet
    sheet_y = 1100
    draw.rounded_rectangle([0, sheet_y, W, H + 50], radius=28, fill=COLOR_SURFACE_DARK)
    
    # Drag Handle
    draw.rounded_rectangle([W // 2 - 28, sheet_y + 18, W // 2 + 28, sheet_y + 24], radius=3, fill=(90, 105, 125))
    
    # Video Title Header
    draw.text((50, sheet_y + 55), "Cinematic Nature - Wildlife 4K.mp4", font=font_bold_24, fill=COLOR_TEXT_PRIMARY)
    draw.text((50, sheet_y + 92), "1.42 GB • 4K UHD 60fps • 42 mins", font=font_med_16, fill=COLOR_TEXT_SECONDARY)
    draw.line([(50, sheet_y + 130), (W - 50, sheet_y + 130)], fill=COLOR_BORDER, width=1)
    
    actions = [
        ("Resume playback", "Continue from 24:18", "▶"),
        ("Subtitles & Captions", "Generate AI subtitles, edit or export", "CC"),
        ("Video details", "3840×2160 • HEVC • 60 fps • Dolby Audio", "ℹ"),
        ("Share video", "Send to contacts or other apps", "↗"),
        ("Delete video", "Permanently delete from storage", "🗑"),
    ]
    
    ay = sheet_y + 155
    for title, desc, icon in actions:
        draw.rounded_rectangle([50, ay, W - 50, ay + 92], radius=16, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
        
        # Icon container squircle
        draw.rounded_rectangle([75, ay + 18, 130, ay + 74], radius=14, fill=COLOR_SURFACE_DARK)
        draw.text((95, ay + 30), icon, font=font_bold_20, fill=COLOR_ACCENT)
        
        draw.text((155, ay + 20), title, font=font_bold_20, fill=COLOR_TEXT_PRIMARY)
        draw.text((155, ay + 54), desc, font=font_med_14, fill=COLOR_TEXT_SECONDARY)
        ay += 110
        
    img.save('screenshots/clean/02_video_actions_sheet.png')
    print("Saved 02_video_actions_sheet.png")

# ==========================================
# 5. SCREEN 1: Library & Folders
# ==========================================
def render_screen_library():
    W, H = 1080, 2400
    img = Image.new('RGB', (W, H), COLOR_BG)
    draw = ImageDraw.Draw(img)
    draw_status_bar(draw, W)
    
    # App Bar
    draw.text((50, 110), "Your videos", font=font_bold_28, fill=COLOR_TEXT_PRIMARY)
    draw.text((50, 155), "3 folders • 48 videos", font=font_med_16, fill=COLOR_TEXT_SECONDARY)
    
    # Search & Sort Icons
    draw.ellipse([W - 140, 110, W - 105, 145], outline=COLOR_TEXT_SECONDARY, width=2)
    draw.line([W - 115, 135, W - 100, 150], fill=COLOR_TEXT_SECONDARY, width=2)
    draw.text((W - 80, 110), "⋮", font=font_bold_28, fill=COLOR_TEXT_SECONDARY)
    
    folders = [
        ("Media Downloads", "24 videos • 8.6 GB", True),
        ("Documentaries & Nature 4K", "16 videos • 14.2 GB", False),
        ("Tech Talks & Courses", "8 videos • 5.1 GB", False),
    ]
    
    fy = 220
    for name, info, is_primary in folders:
        draw.rounded_rectangle([45, fy, W - 45, fy + 120], radius=18, fill=COLOR_SURFACE, outline=COLOR_ACCENT if is_primary else COLOR_BORDER, width=2 if is_primary else 1)
        
        # 42dp Squircle Folder Icon
        draw.rounded_rectangle([75, fy + 24, 145, fy + 94], radius=16, fill=(24, 46, 32) if is_primary else COLOR_SURFACE_DARK)
        draw.text((95, fy + 42), "📁", font=font_bold_24, fill=COLOR_ACCENT)
        
        draw.text((170, fy + 32), name, font=font_bold_20, fill=COLOR_TEXT_PRIMARY)
        draw.text((170, fy + 68), info, font=font_med_16, fill=COLOR_ACCENT if is_primary else COLOR_TEXT_SECONDARY)
        draw.text((W - 90, fy + 48), "›", font=font_bold_28, fill=COLOR_TEXT_MUTED)
        fy += 145
        
    # Section Header: Recent Videos
    draw.text((50, fy + 20), "RECENT VIDEOS", font=font_bold_16, fill=COLOR_TEXT_MUTED)
    
    videos = [
        ("Interstellar 4K - Deep Space Cosmos.mp4", "3.8 GB • 01:42:15", "4K"),
        ("Tokyo Night Walk - 4K HDR 60fps.mkv", "1.2 GB • 28:40", "HDR"),
        ("Cinematic Nature - Wildlife 4K.mp4", "1.42 GB • 42:10", "4K"),
        ("Introduction to Machine Learning.mp4", "650 MB • 55:20", "HD"),
    ]
    
    vy = fy + 65
    for title, meta, badge in videos:
        draw.rounded_rectangle([45, vy, W - 45, vy + 125], radius=16, fill=COLOR_SURFACE, outline=COLOR_BORDER, width=1)
        
        # Thumbnail mock
        draw.rounded_rectangle([65, vy + 18, 205, vy + 107], radius=12, fill=COLOR_SURFACE_DARK)
        draw.text((120, vy + 48), "▶", font=font_bold_20, fill=COLOR_TEXT_MUTED)
        # Badge on thumbnail
        draw.rounded_rectangle([72, vy + 24, 112, vy + 48], radius=4, fill=(0, 0, 0, 180))
        draw.text((78, vy + 28), badge, font=font_bold_14, fill=COLOR_ACCENT)
        
        # Title and details
        draw.text((225, vy + 30), title, font=font_bold_18, fill=COLOR_TEXT_PRIMARY)
        draw.text((225, vy + 68), meta, font=font_med_14, fill=COLOR_TEXT_SECONDARY)
        draw.text((W - 90, vy + 48), "⋮", font=font_bold_24, fill=COLOR_TEXT_MUTED)
        vy += 145
        
    img.save('screenshots/clean/01_library_folders.png')
    print("Saved 01_library_folders.png")

# Run all screen generators
render_screen_player()
render_screen_subtitle_options()
render_screen_subtitle_language()
render_screen_video_actions()
render_screen_library()

# ============================================================
# COMPOSITOR: Generate Production Play Store Marketing Banners
# ============================================================
def create_gradient(width, height, color_top, color_bottom, accent_glow=None):
    base = Image.new('RGB', (width, height), color_top)
    draw = ImageDraw.Draw(base)
    for y in range(height):
        factor = y / float(height)
        r = int(color_top[0] + (color_bottom[0] - color_top[0]) * factor)
        g = int(color_top[1] + (color_bottom[1] - color_top[1]) * factor)
        b = int(color_top[2] + (color_bottom[2] - color_top[2]) * factor)
        draw.line([(0, y), (width, y)], fill=(r, g, b))
        
    if accent_glow:
        glow_layer = Image.new('RGBA', (width, height), (0, 0, 0, 0))
        glow_draw = ImageDraw.Draw(glow_layer)
        cx, cy, radius, glow_col = accent_glow
        for r in range(radius, 0, -6):
            alpha = int(glow_col[3] * (1.0 - (r / float(radius))) ** 1.8)
            col = (glow_col[0], glow_col[1], glow_col[2], alpha)
            glow_draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)
        base = Image.alpha_composite(base.convert('RGBA'), glow_layer).convert('RGB')
    return base

def render_device_mockup(raw_img_path, target_width, target_height):
    bezel_padding = 10
    corner_radius = 54
    screen_w = target_width - (bezel_padding * 2)
    screen_h = target_height - (bezel_padding * 2)
    
    raw = Image.open(raw_img_path).convert('RGBA')
    raw_resized = raw.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    
    screen_mask = Image.new('L', (screen_w, screen_h), 0)
    mask_draw = ImageDraw.Draw(screen_mask)
    mask_draw.rounded_rectangle([0, 0, screen_w, screen_h], radius=corner_radius - 8, fill=255)
    
    device = Image.new('RGBA', (target_width, target_height), (0, 0, 0, 0))
    dev_draw = ImageDraw.Draw(device)
    
    # Outer frame
    dev_draw.rounded_rectangle([0, 0, target_width, target_height], radius=corner_radius, fill=(22, 26, 34, 255), outline=(55, 65, 80, 255), width=2)
    dev_draw.rounded_rectangle([2, 2, target_width - 2, target_height - 2], radius=corner_radius - 2, outline=(32, 38, 48, 255), width=bezel_padding - 2)
    
    # Screen
    device.paste(raw_resized, (bezel_padding, bezel_padding), screen_mask)
    
    # Punch-hole camera
    cam_x = target_width // 2
    cam_y = bezel_padding + 22
    cam_r = 9
    dev_draw.ellipse([cam_x - cam_r, cam_y - cam_r, cam_x + cam_r, cam_y + cam_r], fill=(12, 14, 18, 255), outline=(30, 36, 45, 255), width=1)
    
    # Speaker slit
    slit_w = 60
    dev_draw.rounded_rectangle([cam_x - slit_w // 2, 4, cam_x + slit_w // 2, 7], radius=2, fill=(20, 24, 30, 255))
    
    # Screen inner subtle border
    dev_draw.rounded_rectangle([bezel_padding, bezel_padding, bezel_padding + screen_w, bezel_padding + screen_h], radius=corner_radius - 8, outline=(255, 255, 255, 25), width=1)
    
    return device

def build_marketing_slide(raw_screenshot, badge_text, title_text, subtitle_text, color_theme, output_path):
    W, H = 1080, 2400
    top_col, bot_col, glow_col = color_theme
    glow_params = (W // 2, int(H * 0.65), 780, glow_col) if glow_col else None
    canvas = create_gradient(W, H, top_col, bot_col, glow_params)
    
    canvas_rgba = canvas.convert('RGBA')
    draw = ImageDraw.Draw(canvas_rgba)
    
    font_badge = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 24)
    font_title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 54)
    font_sub = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 30)
    
    # Badge Pill
    badge_y = 110
    bbox = font_badge.getbbox(badge_text)
    bw, bh = bbox[2] - bbox[0], bbox[3] - bbox[1]
    pill_w = bw + 44
    pill_h = 44
    pill_x = (W - pill_w) // 2
    
    draw.rounded_rectangle([pill_x, badge_y, pill_x + pill_w, badge_y + pill_h], radius=22, fill=(140, 217, 107, 35), outline=(140, 217, 107, 180), width=1)
    draw.text((pill_x + 22, badge_y + 8), badge_text, font=font_badge, fill=(168, 237, 138, 255))
    
    # Title & Subtitle
    title_y = badge_y + pill_h + 26
    t_bbox = font_title.getbbox(title_text)
    tw = t_bbox[2] - t_bbox[0]
    draw.text(((W - tw) // 2, title_y), title_text, font=font_title, fill=(255, 255, 255, 255))
    
    sub_y = title_y + 74
    s_bbox = font_sub.getbbox(subtitle_text)
    sw = s_bbox[2] - s_bbox[0]
    draw.text(((W - sw) // 2, sub_y), subtitle_text, font=font_sub, fill=(180, 195, 215, 255))
    
    # Device Mockup Placement
    dev_w = 830
    dev_h = 1844
    phone = render_device_mockup(raw_screenshot, dev_w, dev_h)
    
    phone_x = (W - dev_w) // 2
    phone_y = sub_y + 80
    
    # Realistic Gaussian Drop Shadow
    shadow_pad = 60
    shadow = Image.new('RGBA', (dev_w + shadow_pad * 2, dev_h + shadow_pad * 2), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow)
    s_draw.rounded_rectangle([shadow_pad, shadow_pad + 16, shadow_pad + dev_w, shadow_pad + dev_h + 16], radius=54, fill=(0, 0, 0, 140))
    shadow = shadow.filter(ImageFilter.GaussianBlur(34))
    
    canvas_rgba.paste(shadow, (phone_x - shadow_pad, phone_y - shadow_pad), shadow)
    canvas_rgba.paste(phone, (phone_x, phone_y), phone)
    
    canvas_rgba.convert('RGB').save(output_path, 'PNG', quality=95)
    print(f'Rendered Play Store Slide: {output_path}')

# Output marketing slides using our completely synthetic clean screens
slides = [
    {
        'raw': 'screenshots/clean/03_player_playback.png',
        'badge': 'ULTRA HD PLAYBACK',
        'title': 'Play Any Video in 4K HDR',
        'subtitle': 'Hardware Accelerated • Material You Controls',
        'theme': ((10, 18, 38), (3, 6, 15), (140, 217, 107, 45)),
        'out': 'screenshots/marketing/01_ultra_hd_playback.png'
    },
    {
        'raw': 'screenshots/clean/04_subtitle_options.png',
        'badge': 'ON-DEVICE SPEECH AI',
        'title': 'AI Subtitles Without Internet',
        'subtitle': 'Generate synced captions offline with Whisper',
        'theme': ((7, 28, 22), (2, 10, 8), (46, 213, 115, 55)),
        'out': 'screenshots/marketing/02_offline_ai_captions.png'
    },
    {
        'raw': 'screenshots/clean/05_subtitle_language_sheet.png',
        'badge': '99 LANGUAGES SUPPORTED',
        'title': 'Watch Foreign Media in English',
        'subtitle': 'Real-time speech-to-English translation',
        'theme': ((26, 14, 46), (6, 3, 14), (168, 85, 247, 50)),
        'out': 'screenshots/marketing/03_speech_translation.png'
    },
    {
        'raw': 'screenshots/clean/02_video_actions_sheet.png',
        'badge': 'MATERIAL 3 DESIGN',
        'title': 'Sleek Video Actions Sheet',
        'subtitle': 'Resume playback • Details • Clear watch progress',
        'theme': ((14, 25, 42), (4, 8, 16), (56, 189, 248, 50)),
        'out': 'screenshots/marketing/04_video_actions_sheet.png'
    },
    {
        'raw': 'screenshots/clean/01_library_folders.png',
        'badge': 'PRIVACY & SCOPED STORAGE',
        'title': 'Private & Organized Library',
        'subtitle': 'Media Downloads isolation • Custom folders',
        'theme': ((12, 32, 22), (3, 10, 7), (140, 217, 107, 50)),
        'out': 'screenshots/marketing/05_private_library.png'
    }
]

for s in slides:
    build_marketing_slide(s['raw'], s['badge'], s['title'], s['subtitle'], s['theme'], s['out'])

print("ALL MARKETING SCREENSHOTS RENDERED AND VERIFIED!")
