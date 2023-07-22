<h1 align="center">
  <img alt="Icon" width="75" src="https://github.com/thepbone/RootlessJamesDSP/blob/master/img/icons/web/icon-192.png?raw=true">
  <br>
  RootlessJamesDSP
  <br>
</h1>
<h4 align="center">System-wide JamesDSP implementation for non-rooted Android devices</h4>
<p align="center">
  <a href="https://play.google.com/store/apps/details?id=me.timschneeberger.rootlessjamesdsp&utm_source=github&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1">
  	<img alt="Google play release" src="https://img.shields.io/github/v/release/ThePBone/RootlessJamesDSP?label=google%20play">
  </a>
  <a href="https://f-droid.org/packages/me.timschneeberger.rootlessjamesdsp/">
  	<img alt="F-Droid release" src="https://img.shields.io/f-droid/v/me.timschneeberger.rootlessjamesdsp">
  </a>
  <a href="https://github.com/ThePBone/RootlessJamesDSP/blob/master/LICENSE">
      <img alt="License" src="https://img.shields.io/github/license/ThePBone/RootlessJamesDSP">
  </a>
    <a href="https://github.com/ThePBone/RootlessJamesDSP/actions/workflows/build.yml">
      <img alt="GitHub Workflow Status" src="https://img.shields.io/github/actions/workflow/status/thepbone/rootlessjamesdsp/build.yml">
  </a>

</p>
<p align="center">
  <a href="#limitations">Limitations</a> •
  <a href="#spotify-support-patch">Spotify patch</a> •
  <a href="#downloads">Downloads</a> •
  <a href="#credits">Credits</a>
</p>

<p align="center">
  <a href='https://play.google.com/store/apps/details?id=me.timschneeberger.rootlessjamesdsp&utm_source=github&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'> 
    <img width="300" alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/>
  </a>
</p>

<p align="center">
This app uses <a href="https://github.com/james34602/JamesDSPManager">libjamesdsp</a> which is written by <a href="https://github.com/james34602">James Fung (@james34602)</a>.
</p>

<p align="center">
    This app has several limitations that may be deal-breaking to some people; please read this whole document before using the app.</i>
</p>

<p align="center">
   <img alt="Screenshot" width="250" src="img/screenshot1.png">
   <img alt="Screenshot" width="250" src="img/screenshot7.png">
</p>


## Limitations
* Apps blocking internal audio capture remain unprocessed (e.g., Spotify, Google Chrome)
* Cannot coexist with (some) other audio effect apps (e.g., Wavelet and other apps that make use of the `DynamicsProcessing` Android API)
* Increased audio latency 


Apps confirmed working:
* YouTube
* YouTube Music
* Amazon Music
* Deezer
* Poweramp
* Substreamer
* Twitch
* Spotify ReVanced **(Patch required)**
* Apple Music
* ...

Unsupported apps include:
* Spotify (patch for Spotify exists)
* Google Chrome
* SoundCloud
* ...

Tested on:
* Samsung Galaxy S20+ (Android 12; OneUI 4.0)
* Stock AOSP emulator (Android 10-13)
* Google Pixel 6 Pro (Android 13)

## Spotify support patch
> **Note** This patch is universal and may also work with other apps than Spotify.

You can only use Spotify with this application if you patch the Spotify app.
The setup is very easy:

1. Download and install the [ReVanced manager APK](https://github.com/revanced/revanced-manager/releases) 
2. Install the unpatched Spotify app
3. Open ReVanced Manager, select Spotify and enable the `remove-screen-capture-restriction` patch.
4. Start the patching process and install the patched APK once it is done.
5. You can now use Spotify with RootlessJamesDSP.

### Patching other unsupported apps

The `remove-screen-capture-restriction` patch is universal and can also be used with custom APKs other than Spotify.
The patch cannot remove capture restrictions for apps that use the native AAudio C++ API for playback. 

1. Download and install the [ReVanced manager APK](https://github.com/revanced/revanced-manager/releases) 
2. Open ReVanced Manager, tap on 'Select an application' and press the 'Storage' action button in the bottom-right corner.
3. Select your APK using the file picker.
4. Enable the `remove-screen-capture-restriction` patch.
5. Start the patching process and install the patched APK once it is done. Make sure to uninstall the unpatched app if it is installed, otherwise you will run into a signature conflict during installtion.

> **Warning** If the patched app crashes on startup (or refuses to work properly), it is likely that the app uses signature checks or other protections against tampering. In that case, additional patches that disable these anti-tampering checks would need to be created by hand.

## Differences to other rootless FX apps

Regular rootless audio effect apps on the Play Store all essentially work the same way:
Android has several default audio effects built into its operating system that these apps can use without any special permissions. Here's a list of those: https://developer.android.com/reference/android/media/audiofx/AudioEffect.

Being restricted to these default built-in audio effects is problematic if you want to implement any advanced custom effects such as Viper or JDSP, because Android does not allow apps to access & modify the audio stream directly.

To work around this problem, RootlessJamesDSP uses a bunch of tricks to gain full access to the audio stream of other apps. This is done via Android's internal audio capture.
This allows RootlessJamesDSP to apply its custom audio effects directly without relying on Android's built-in effects.

Unfortunately, these tricks are not 100% reliable and introduce some limitations.
Apps such as Spotify block internal audio capture (they don't want people to record their songs), and because of that, RootlessJamesDSP cannot directly access the audio stream of that app.
This is the reason why a special patch is required to disable this DRM restriction inside Spotify's app. Patches for other apps with these DRM restrictions do not exist, but are possible to do.

## Translations

This application can be translated via Crowdin: https://crowdin.com/project/rootlessjamesdsp

Not all languages are enabled at the moment in Crowdin. To request a new language, please open an issue here on GitHub.

## Downloads

This app is available for free on Google Play: [https://play.google.com/store/apps/details?id=me.timschneeberger.rootlessjamesdsp](https://play.google.com/store/apps/details?id=me.timschneeberger.rootlessjamesdsp&utm_source=github&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

Also available on F-Droid: https://f-droid.org/packages/me.timschneeberger.rootlessjamesdsp/

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/me.timschneeberger.rootlessjamesdsp/)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
    alt="Get it on Google Play"
    height="80">](https://play.google.com/store/apps/details?id=me.timschneeberger.rootlessjamesdsp&utm_source=github&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1)

## Using Root

This app focuses on a rootless implementation, but it can be made to work with the magisk module too. [See here for details](BUILD_ROOT.md).

All the limitations mentioned above are **not relevant** for the magisk/root version. 

## Credits

* JamesDSP - [James Fung (@james34602)](https://github.com/james34602)
* Theming system & backup system based on Tachiyomi

### Translators

<!-- CROWDIN-CONTRIBUTORS-START -->
<table>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/ThePBone"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15683553/medium/d13428d1e0922bc2069500aef57d1459.png" />
        <br />
        <sub><b>Tim Schneeberger (ThePBone)</b></sub></a>
      <br />
      <sub><b>22239 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/netrunner-exe"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15209210/medium/dabb33b18a6eb0e59cee34e448d81e40.jpg" />
        <br />
        <sub><b>Oleksandr Tkachenko (netrunner-exe)</b></sub></a>
      <br />
      <sub><b>13556 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/rex07"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/13820943/medium/5b5499d4f13f168e0eab0499857a831e.jpeg" />
        <br />
        <sub><b>Rex_sa (rex07)</b></sub></a>
      <br />
      <sub><b>3449 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/FrameXX"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/14591682/medium/071f9d859dc36f9281f6f84b9c18c852.png" />
        <br />
        <sub><b>FrameXX</b></sub></a>
      <br />
      <sub><b>3367 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/fankesyooni"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15676501/medium/6ee6d7e4c63bfb0f90dc5088a5ff0efd.jpg" />
        <br />
        <sub><b>fankesyooni</b></sub></a>
      <br />
      <sub><b>3291 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/beruanglaut"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15727477/medium/928d69a437d753d783f03c22bf2d2c10.png" />
        <br />
        <sub><b>Beruanglaut (beruanglaut)</b></sub></a>
      <br />
      <sub><b>3168 words</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/hasandgn37"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15507252/medium/4a02e1c8d12aae3330baa229e5f8fb5e.jpeg" />
        <br />
        <sub><b>MajorCanel (hasandgn37)</b></sub></a>
      <br />
      <sub><b>2679 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/marcin.petrusiewicz"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/13535169/medium/29d3f1c6a1a270a85b8fda88e8d1c848.jpeg" />
        <br />
        <sub><b>Marcin Petrusiewicz (marcin.petrusiewicz)</b></sub></a>
      <br />
      <sub><b>2360 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/TecitoDeMenta"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15859109/medium/09cc6632c3686add5d52d4e7a3dec25a.jpg" />
        <br />
        <sub><b>Alondra Márquez (TecitoDeMenta)</b></sub></a>
      <br />
      <sub><b>1796 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/jont4"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15464490/medium/bd7f97dff61f637d007652f9947d8f17.jpeg" />
        <br />
        <sub><b>Jontix (jont4)</b></sub></a>
      <br />
      <sub><b>1730 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/SerAX3L"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15755831/medium/4f31c78564ed55fef4b2bf8d96213a55.jpeg" />
        <br />
        <sub><b>Alessandro Belfiore (SerAX3L)</b></sub></a>
      <br />
      <sub><b>1228 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/liziq"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15757161/medium/f3903c160404f095de68760f81609430.jpeg" />
        <br />
        <sub><b>zhiq liu (liziq)</b></sub></a>
      <br />
      <sub><b>1084 words</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/TheGary"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15713727/medium/4f9ede8b07ace57124001fb6678aeff7_default.png" />
        <br />
        <sub><b>Gary Bonilla (TheGary)</b></sub></a>
      <br />
      <sub><b>1030 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/kyunairi"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15925091/medium/7b1dd408c51242ab8602eb68408987cb_default.png" />
        <br />
        <sub><b>kyunairi</b></sub></a>
      <br />
      <sub><b>888 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Add000"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15913337/medium/df3063b9d3c51c71f36f529471f11636_default.png" />
        <br />
        <sub><b>Add000</b></sub></a>
      <br />
      <sub><b>753 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/roccovantechno"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15818971/medium/75663306f941c87c2d9088c923aa89ad.jpeg" />
        <br />
        <sub><b>Gyuri Gergely (roccovantechno)</b></sub></a>
      <br />
      <sub><b>714 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/pizzawithdirt"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15711961/medium/e6c27e5ff36a68db03f9b786007b9cbd.png" />
        <br />
        <sub><b>Ali Yuruk (pizzawithdirt)</b></sub></a>
      <br />
      <sub><b>639 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Louis_Unnoficial"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/13340060/medium/d1e89dfe12c220c667fa25123652cac5.png" />
        <br />
        <sub><b>Loui's (Louis_Unnoficial)</b></sub></a>
      <br />
      <sub><b>513 words</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/ianpok17"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15647373/medium/daf979a91f0a64b448cf88a954d45e2b.jpeg" />
        <br />
        <sub><b>Criss Santiesteban (ianpok17)</b></sub></a>
      <br />
      <sub><b>470 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Mr-Ojii"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15864101/medium/297229b45644e26de1de3f289ab3cef1.png" />
        <br />
        <sub><b>Mr-Ojii</b></sub></a>
      <br />
      <sub><b>358 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/tomechio"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/14617144/medium/b54739ea4f3f61a426417fea52ba2f9d.png" />
        <br />
        <sub><b>tomechio</b></sub></a>
      <br />
      <sub><b>358 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/IrepY"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/14859670/medium/5185d459ceb32983f5351696469e267f.jpg" />
        <br />
        <sub><b>IrepY</b></sub></a>
      <br />
      <sub><b>310 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Na7M"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15764361/medium/073439c3ec9bfdeb797529174de550f8_default.png" />
        <br />
        <sub><b>Na7M</b></sub></a>
      <br />
      <sub><b>306 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/MES-INARI"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15690555/medium/526a975cad780dd5bc3bf2388dfea4a5.png" />
        <br />
        <sub><b>MES-mitutti (MES-INARI)</b></sub></a>
      <br />
      <sub><b>288 words</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/adrian_ek"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/12337252/medium/1b031cfc7177a30c2175c905e527dfb8_default.png" />
        <br />
        <sub><b>adrian_ek</b></sub></a>
      <br />
      <sub><b>268 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Nlntendq"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/14386422/medium/0c58b2245a59d1596c329dcf24037eb6.png" />
        <br />
        <sub><b>Nlntendq</b></sub></a>
      <br />
      <sub><b>225 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Andy-Dunne"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15858259/medium/6620b32c17f830d32fc65324ac5310c3.png" />
        <br />
        <sub><b>Andy-Dunne</b></sub></a>
      <br />
      <sub><b>224 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Tymwitko"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15706765/medium/a2288209d82b78b8e8d959c009382086_default.png" />
        <br />
        <sub><b>Tymwitko</b></sub></a>
      <br />
      <sub><b>210 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/rabaimor"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15747879/medium/1e37eb170fa827e02ea2c40cce89b8ac_default.png" />
        <br />
        <sub><b>rabaimor</b></sub></a>
      <br />
      <sub><b>183 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/dev_trace"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15729737/medium/f515d9ef1eeb393759e7180bc700afc2_default.png" />
        <br />
        <sub><b>dev_trace</b></sub></a>
      <br />
      <sub><b>140 words</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/mpolin"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/15871907/medium/af802355a40a11ea70c5c803e8966d80_default.png" />
        <br />
        <sub><b>Muchlis Polin (mpolin)</b></sub></a>
      <br />
      <sub><b>135 words</b></sub>
    </td>
    <td align="center" valign="top">
      <a href="https://crowdin.com/profile/Jamil.M.Gomez"><img alt="logo" style="width: 64px" src="https://crowdin-static.downloads.crowdin.com/avatar/13442100/medium/70d15cc33101a9739868321b10543f18.png" />
        <br />
        <sub><b>Jamil M. Gomez (Jamil.M.Gomez)</b></sub></a>
      <br />
      <sub><b>131 words</b></sub>
    </td>
  </tr>
</table><a href="https://crowdin.com/project/rootlessjamesdsp" target="_blank">Translate in Crowdin 🚀</a>
<!-- CROWDIN-CONTRIBUTORS-END -->
