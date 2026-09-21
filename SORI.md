# Sori

Sori는 [Metrolist](https://github.com/MetrolistGroup/Metrolist)(GPL-3.0)를 포크한 앱이다. 원본 버그 수정을 계속 병합할 수 있게 변경을 최소로 유지한다.

## 원본과 다른 점

| 파일 | 변경 |
|---|---|
| `app/build.gradle.kts` | `applicationId` → `com.saootikim.sori`, 앱 이름 → `Sori`, 버전 규칙 (아래 참고) |
| `app/src/main/kotlin/com/metrolist/music/utils/Updater.kt` | 업데이트 확인 대상을 `saootikim/Sori`로 변경, `Sori.apk` 인식, Metrolist-KMP 업데이트 안내 끔 |
| `app/src/main/res/values{,-ko}/metrolist_strings.xml`, `values-ko/strings.xml`, `values/app_name.xml` | 화면에 보이는 "Metrolist"를 "Sori"로 변경 (크레딧은 유지) |
| `app/src/main/res/drawable*/ic_launcher_*`, `values/colors.xml`, `values/ic_launcher_background.xml` | 아이콘 (음파 막대, 보라→코랄 그라데이션) |
| `app/src/main/kotlin/com/metrolist/music/utils/LoginPagePolicy.kt` (신규), `ui/screens/LoginScreen.kt` | 로그인 후 구매 페이지 등 다른 youtube.com 페이지에 멈추는 문제 수정 (Premium 체험 대상 계정) |
| `ui/screens/settings/AboutScreen.kt`, `res/values{,-ko}/sori_strings.xml` (신규) | 소프트웨어 정보: 개발자 saootikim, "Metrolist 기반"·소스·라이선스 링크 (원작 개발자·기부·커뮤니티 링크 제거) |
| `res/drawable/small_icon.xml` | 알림바·플레이어 기본 이미지·공유 이미지 로고를 Sori 아이콘으로 |
| `.github/workflows/release.yml` | GitHub 기본 러너 사용, FOSS 빌드만, 결과물 `Sori.apk` |
| `.gitignore` | `*.jks`, `*.keystore` 제외 |

Kotlin 패키지(`com.metrolist.music`)는 그대로 둔다. 바꾸면 원본을 병합할 수 없게 된다.

## 버전 규칙

- `versionName`: Sori 자체 번호. 버그 수정은 `1.0.1`, 기능 추가는 `1.1.0` 식으로 올린다.
- `versionCode`: 릴리스마다 1씩 올린다 (1.0.0 = `15303`). 이전 릴리스보다 작으면 덮어쓰기 업데이트가 안 된다.
- 원본을 병합할 때 원본의 `versionCode`와 `versionName` 줄은 버리고 Sori 값을 유지한다.
- 앱은 릴리스 제목(= versionName)이 자기 버전보다 크면 업데이트 알림을 띄운다.
- 13.7.0.1 / 13.7.0.2 설치본은 1.0.0을 새 버전으로 인식하지 못한다. 그래서 한 번은 링크(`https://github.com/saootikim/Sori/releases/latest/download/Sori.apk`)로 직접 받아서 덮어쓰기 설치해야 한다. 로그인과 데이터는 유지된다.

## 릴리스 방법

1. `app/build.gradle.kts`의 `versionName`과 `versionCode`를 올린다.
2. `main` 브랜치에 push한다.
3. `release.yml`이 버전 변경을 감지하고 서명된 `Sori.apk`를 만들어 GitHub Release로 올린다.
4. 친구들 앱이 최대 2시간 안에 새 릴리스를 확인하고 업데이트 알림을 띄운다.

수동으로 실행하려면: `gh workflow run release.yml -R saootikim/Sori`

이 포크에서는 push로 워크플로가 자동 실행되지 않은 적이 있다. 버전을 올려 push했는데 Actions 탭에 실행 기록이 없으면 위 명령으로 직접 실행한다.

## 원본 동기화 (스트림이 안 나오는 등 원본이 수정했을 때)

```bash
git fetch upstream
git merge upstream/main
# 충돌은 위 표에 있는 파일에서만 난다. Sori 쪽 변경을 유지하면서 해결한다.
# 원본 versionName이 바뀌었으면 위 버전 규칙대로 다시 맞춘다.
git push origin main
```

## 서명 키

- 릴리스 APK는 GitHub Secrets에 등록한 키로 서명된다: `KEYSTORE`(base64), `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`
- **`sori-release.jks`를 잃어버리면 친구들이 앱을 업데이트할 수 없다.** 앱을 지우고 새로 설치해야 하고, 그러면 다운로드한 곡과 설정이 사라진다. 저장소 바깥 두 군데 이상에 백업해 둘 것.

## 로컬 빌드

JDK 21이 필요하다 (`jvmToolchain(21)`). 사용자 폴더처럼 한글이 들어간 경로는 AGP가 빌드를 막기 때문에, 저장소는 ASCII 경로(`C:\dev\sori`)에 둔다.

```bash
JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot" ./gradlew :app:assembleFossDebug
```
