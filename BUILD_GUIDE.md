# 🚀 BUILD GUIDE - Android Studio with JDK

## ⚠️ CURRENT ISSUE
**Java Runtime not found on your Mac**

You need to build using **Android Studio** which has its own embedded JDK.

---

## ✅ STEP-BY-STEP BUILD INSTRUCTIONS

### **Method 1: Using Android Studio (RECOMMENDED)**

#### **Step 1: Open Project**
```
1. Launch Android Studio
2. File → Open
3. Navigate to: /Users/nitishbhardwaj/Desktop/weelo
4. Click "Open"
```

#### **Step 2: Sync Gradle**
```
1. Android Studio will show "Gradle sync needed"
2. Click "Sync Now" button
3. Wait for sync to complete (1-2 minutes)
```

**If you see errors:**
- Click "File → Invalidate Caches → Invalidate and Restart"
- Try sync again

#### **Step 3: Fix Any Warnings**

**Check for warnings:**
```
1. Look at bottom panel "Build" tab
2. Look for yellow warnings ⚠️
3. Click on each warning to see details
```

**Common warnings and fixes:**

**Warning 1: "Unused import"**
```kotlin
// Android Studio will highlight in gray
import com.some.unused.Class  // ← Remove this line
```
**Fix:** Delete the unused import line

**Warning 2: "Variable can be declared as 'val'"**
```kotlin
// Warning
var someValue = "test"  // Never reassigned

// Fix
val someValue = "test"  // Use val for constants
```

**Warning 3: "Missing @JvmStatic annotation"**
```kotlin
companion object {
    // Warning
    fun getInstance(): Something { }
    
    // Fix
    @JvmStatic
    fun getInstance(): Something { }
}
```

**Warning 4: "Hardcoded text should use @string resource"**
```xml
<!-- Warning -->
<TextView android:text="Pickup" />

<!-- Fix -->
<TextView android:text="@string/pickup_title" />
```
Add to `strings.xml`:
```xml
<string name="pickup_title">Pickup</string>
```

#### **Step 4: Build APK**
```
1. Build → Clean Project (wait to finish)
2. Build → Rebuild Project (wait to finish)
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. Wait for "BUILD SUCCESSFUL" message
5. Click "locate" link to find APK
```

**APK Location:**
```
/Users/nitishbhardwaj/Desktop/weelo/app/build/outputs/apk/debug/app-debug.apk
```

#### **Step 5: Run on Device**
```
1. Connect Android device via USB
2. Enable USB Debugging on device
3. Click green "Run" button (▶️) in Android Studio
4. Select your device
5. App will install and launch
```

---

### **Method 2: Install JDK for Command Line (Optional)**

If you want to build from terminal:

#### **Step 1: Install Homebrew (if not installed)**
```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

#### **Step 2: Install OpenJDK 17**
```bash
brew install openjdk@17
```

#### **Step 3: Link Java**
```bash
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
```

#### **Step 4: Set JAVA_HOME**
Add to `~/.zshrc`:
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Reload:
```bash
source ~/.zshrc
```

#### **Step 5: Verify**
```bash
java -version
# Should show: openjdk version "17.x.x"
```

#### **Step 6: Build from Terminal**
```bash
cd /Users/nitishbhardwaj/Desktop/weelo
./gradlew clean assembleDebug
```

APK will be at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔧 FIXING COMMON WARNINGS

### **1. Fix Hardcoded Strings**

**Current:**
```xml
<TextView android:text="Pickup" />
<TextView android:text="For me" />
<TextView android:text="Instant" />
<TextView android:text="Custom" />
```

**Fixed:** Create/update `app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Weelo</string>
    
    <!-- Location Input Screen -->
    <string name="pickup_title">Pickup</string>
    <string name="for_me">For me</string>
    <string name="instant">Instant</string>
    <string name="custom">Custom</string>
    <string name="pickup_location_hint">Pickup location</string>
    <string name="drop_location_hint">Drop location</string>
    <string name="select_on_map">Select on map</string>
    <string name="add_stops">Add stops</string>
    <string name="continue_btn">Continue</string>
    <string name="next_btn">Next</string>
    
    <!-- Warning Messages -->
    <string name="location_warning">Uh oh, we can\'t find you! Enter your pickup location for a smooth ride.</string>
    <string name="recent_locations">Recent locations</string>
</resources>
```

Then update XML to use `@string/`:
```xml
<TextView android:text="@string/pickup_title" />
```

---

### **2. Fix Unused Imports**

Android Studio will show these in gray. Simply:
1. **Code → Optimize Imports** (Ctrl+Alt+O / Cmd+Option+O)
2. Or manually delete gray import lines

---

### **3. Fix "Can be private" Warnings**

```kotlin
// Warning
lateinit var someView: TextView

// Fix (if only used in this file)
private lateinit var someView: TextView
```

---

### **4. Fix Nullable Issues**

```kotlin
// Warning: Unnecessary safe call
val text = someNonNullString?.length

// Fix
val text = someNonNullString.length
```

---

### **5. Fix Deprecation Warnings**

Check Android Studio suggestions, usually:
```kotlin
// Deprecated
startActivityForResult(intent, REQUEST_CODE)

// Fix
activityResultLauncher.launch(intent)
```

---

## 📋 PRE-BUILD CHECKLIST

Before building, verify:

- [ ] All XML files properly formatted
- [ ] No red errors in Android Studio
- [ ] Gradle sync successful
- [ ] `google-services.json` present in `app/` folder
- [ ] All API keys configured
- [ ] Minimum SDK version compatible with device

---

## 🐛 TROUBLESHOOTING

### **Error: "Gradle sync failed"**
**Solution:**
```
1. File → Invalidate Caches → Invalidate and Restart
2. Delete .gradle and .idea folders
3. Sync again
```

### **Error: "Failed to resolve dependencies"**
**Solution:**
```
1. Check internet connection
2. Tools → SDK Manager → Update SDK
3. Sync again
```

### **Error: "APK installation failed"**
**Solution:**
```
1. Uninstall old version from device
2. Build → Clean Project
3. Build → Rebuild Project
4. Run again
```

### **Error: "Manifest merger failed"**
**Solution:**
```
1. Check AndroidManifest.xml for conflicts
2. Look for duplicate permissions/activities
3. Fix conflicts and rebuild
```

---

## ✅ WHAT TO DO NOW

### **RECOMMENDED: Use Android Studio**

**Step 1:** Open Android Studio

**Step 2:** Open project
```
File → Open → /Users/nitishbhardwaj/Desktop/weelo
```

**Step 3:** Wait for Gradle sync

**Step 4:** Fix any warnings shown in "Build" tab
- Click on warning
- Apply suggested fix
- Or use "Code → Inspect Code" to see all warnings

**Step 5:** Build APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```

**Step 6:** Test on device
```
Run → Run 'app' (or press ▶️ button)
```

---

## 📱 TESTING THE CHANGES

Once app runs, test:

### **Location Input Screen:**
- [ ] Title says "Pickup" ✅
- [ ] "For me" button visible in header ✅
- [ ] Instant/Custom toggle visible and working ✅
- [ ] Tap Instant → Smooth animation ✅
- [ ] Tap Custom → Smooth animation ✅
- [ ] TO field fades in/out smoothly ✅
- [ ] Only locations section scrolls ✅
- [ ] Input card has elevation/shadow ✅
- [ ] Pink warning banner appears (if location off) ✅

---

## 📊 BUILD OUTPUT LOCATION

After successful build:

**Debug APK:**
```
/Users/nitishbhardwaj/Desktop/weelo/app/build/outputs/apk/debug/app-debug.apk
```

**Release APK (signed):**
```
/Users/nitishbhardwaj/Desktop/weelo/app/build/outputs/apk/release/app-release.apk
```

**AAB (for Play Store):**
```
/Users/nitishbhardwaj/Desktop/weelo/app/build/outputs/bundle/release/app-release.aab
```

---

## 🎯 QUICK REFERENCE

| Task | Command/Action |
|------|----------------|
| Open project | File → Open |
| Sync Gradle | File → Sync Project with Gradle Files |
| Clean build | Build → Clean Project |
| Build APK | Build → Build APK(s) |
| Run on device | Click ▶️ Run button |
| Fix imports | Code → Optimize Imports |
| Inspect code | Code → Inspect Code |
| Format code | Code → Reformat Code |

---

**Let me know when you've opened it in Android Studio and I'll help you fix any warnings that appear!** 🚀
