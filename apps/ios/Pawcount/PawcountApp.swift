import SwiftUI
import UserNotifications

/*
 PawcountApp.swift - the app entry, the theme/root view and screen routing.

 Navigation is just AppStore.stack: the last Route is shown, back pops. There is no framework router because
 there are only six screens.
*/

@main
struct PawcountApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject var store = AppStore()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(store) }
    }
}

/// lets reminders show as a banner even while the app is open
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}

struct RootView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.colorScheme) var scheme

    var body: some View {
        let theme = store.settings.theme
        let dark = theme == "dark" || (theme == "system" && scheme == .dark)
        let pc = dark ? PawColors.dark : PawColors.light
        let route = store.route
        ZStack {
            pc.bg.ignoresSafeArea()
            Group {
                switch route {
                case .welcome: WelcomeView()
                case .home: HomeView()
                case .memories: MemoriesView()
                case .settings: SettingsView()
                case .detail(let id): DetailView(id: id)
                case .flow(let id): FlowView(editId: id)
                }
            }
            .id(route)
            .transition(.opacity.combined(with: .offset(y: 14)))
            if route == .home || route == .memories {
                VStack { Spacer(); TabBar(current: route) }.ignoresSafeArea(.keyboard)
            }
            ConfettiOverlay(burst: store.confettiBurst, reduceMotion: store.reduceMotion).ignoresSafeArea()
            ToastView()
        }
        .animation(.easeInOut(duration: 0.28), value: route)
        .environment(\.paw, pc)
        .environment(\.pawReduceMotion, store.reduceMotion)
        .preferredColorScheme(theme == "dark" ? .dark : (theme == "light" ? .light : nil))
    }
}
