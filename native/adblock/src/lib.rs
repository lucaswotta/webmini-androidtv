use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::sync::{Mutex, OnceLock, RwLock};

static FILTERS: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();
static PENDING_FILTERS: OnceLock<Mutex<Option<FilterSet>>> = OnceLock::new();

fn filters() -> &'static RwLock<Option<Engine>> {
    FILTERS.get_or_init(|| RwLock::new(None))
}

fn pending_filters() -> &'static Mutex<Option<FilterSet>> {
    PENDING_FILTERS.get_or_init(|| Mutex::new(None))
}

fn java_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Option<String> {
    env.get_string(&value).ok().map(Into::into)
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_MainActivity_nativeBeginFilterLists(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    if let Ok(mut pending) = pending_filters().lock() {
        *pending = Some(FilterSet::new(false));
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_MainActivity_nativeAddFilterList(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    list: JString<'_>,
) -> jboolean {
    let Some(list) = java_string(&mut env, list) else {
        return JNI_FALSE;
    };
    let Ok(mut pending) = pending_filters().lock() else {
        return JNI_FALSE;
    };
    let Some(filters) = pending.as_mut() else {
        return JNI_FALSE;
    };
    filters.add_filter_list(list, ParseOptions::default());
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_MainActivity_nativeFinishFilterLists(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jboolean {
    let Ok(mut pending) = pending_filters().lock() else {
        return JNI_FALSE;
    };
    let Some(filter_set) = pending.take() else {
        return JNI_FALSE;
    };
    let engine = Engine::new_with_filter_set(filter_set);
    let Ok(mut current) = filters().write() else {
        return JNI_FALSE;
    };
    *current = Some(engine);
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_MainActivity_nativeClearFilters(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    if let Ok(mut current) = filters().write() {
        *current = None;
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_MainActivity_nativeShouldBlock(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    url: JString<'_>,
    page_url: JString<'_>,
    resource_type: JString<'_>,
    method: JString<'_>,
) -> jboolean {
    let (Some(url), Some(page_url), Some(resource_type), Some(method)) = (
        java_string(&mut env, url),
        java_string(&mut env, page_url),
        java_string(&mut env, resource_type),
        java_string(&mut env, method),
    ) else {
        return JNI_FALSE;
    };
    let Ok(request) = Request::new(&url, &page_url, &resource_type, &method) else {
        return JNI_FALSE;
    };
    let Ok(current) = filters().read() else {
        return JNI_FALSE;
    };
    match current.as_ref() {
        Some(engine) if engine.check_network_request(&request).should_block() => JNI_TRUE,
        _ => JNI_FALSE,
    }
}
