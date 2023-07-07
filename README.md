# VirtuallyPrivate

<div align="center" href="https://github.com/YeahItsMeAgain/VirtuallyPrivate">
    <a href="https://github.com/YeahItsMeAgain/VirtuallyPrivate">
      <img src=https://raw.githubusercontent.com/YeahItsMeAgain/VirtuallyPrivate/master/assets/logo.png?token=GHSAT0AAAAAABY4HWYWR73UEMYHONZUMNTEZFIJMKQ
        width="400" alt="VirtuallyPrivate">
    </a>
</div>

</br>
</br>

An Android privacy management app developed a couple years ago as a collaborative school project with [@0xToxey]( https://github.com/0xToxey ).  
Allows the user to selectively obstruct or simulate permissions for installed applications without them noticing.

## Features
- Totally blind applications (generates generic null response)
- Permission simulation (customizable responses for each permission)
- Notification alerts for unauthorized permission usage

## Supported Restrictions
- Clipboard
- Apps
- Camera
- Microphone
- Contacts
- Call log
- Location
- Wifi
- Identifications
- NFC
- Storage (separate controls for Read and Write operations)

# Demo
![Demo](./assets/demo.gif)


# Notes
- The app was meant to run inside [VirtualXposed]( https://github.com/android-hacker/VirtualXposed).
- If you actually want to use something like this, you should probably use [XPrivacyLua](https://github.com/M66B/XPrivacyLua) (as this is **heavily** inspired by it).