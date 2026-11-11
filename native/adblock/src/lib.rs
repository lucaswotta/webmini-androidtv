use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions, RuleTypes};
use adblock::request::Request;
use jni::objects::{JClass, JObjectArray, JString};
use jni::sys::{jboolean, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use std::fs;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{OnceLock, RwLock};

static FILTERS: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();
// Bumped by every clear, so an engine that was being built or read meanwhile is not installed.
static GENERATION: AtomicU32 = AtomicU32::new(0);

fn filters() -> &'static RwLock<Option<Engine>> {
    FILTERS.get_or_init(|| RwLock::new(None))
}

fn java_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Option<String> {
    env.get_string(&value).ok().map(Into::into)
}

// The previous engine is dropped after the write lock is released; freeing it takes long
// enough to stall the requests waiting on the read side.
fn install(engine: Engine, generation: u32) -> bool {
    let previous = {
        let Ok(mut current) = filters().write() else {
            return false;
        };
        if GENERATION.load(Ordering::SeqCst) != generation {
            return false;
        }
        current.replace(engine)
    };
    drop(previous);
    true
}

fn write_atomically(path: &str, bytes: &[u8]) -> std::io::Result<()> {
    let temporary = format!("{path}.tmp");
    fs::write(&temporary, bytes)?;
    fs::rename(&temporary, path).inspect_err(|_| {
        let _ = fs::remove_file(&temporary);
    })
}

// Reads one list at a time, so only one list text is in memory, and keeps network rules only:
// the app never asks the engine for cosmetic rules.
fn build(list_paths: &[String]) -> Option<Engine> {
    let mut filter_set = FilterSet::new(false);
    let mut loaded = false;
    for path in list_paths {
        let Ok(bytes) = fs::read(path) else {
            continue;
        };
        let text = String::from_utf8(bytes)
            .unwrap_or_else(|error| String::from_utf8_lossy(error.as_bytes()).into_owned());
        filter_set.add_filter_list(
            text,
            ParseOptions {
                rule_types: RuleTypes::NetworkOnly,
                ..ParseOptions::default()
            },
        );
        loaded = true;
    }
    loaded.then(|| Engine::new_with_filter_set(filter_set))
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_FilterEngine_nativeBuild(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    list_paths: JObjectArray<'_>,
    engine_path: JString<'_>,
) -> jboolean {
    let generation = GENERATION.load(Ordering::SeqCst);
    let Ok(count) = env.get_array_length(&list_paths) else {
        return JNI_FALSE;
    };
    let mut paths = Vec::new();
    for index in 0..count {
        let Ok(element) = env.get_object_array_element(&list_paths, index) else {
            return JNI_FALSE;
        };
        let Some(path) = java_string(&mut env, JString::from(element)) else {
            return JNI_FALSE;
        };
        paths.push(path);
    }
    let Some(engine_path) = java_string(&mut env, engine_path) else {
        return JNI_FALSE;
    };
    let Some(engine) = build(&paths) else {
        return JNI_FALSE;
    };
    // A failed write only costs a rebuild on the next start.
    if write_atomically(&engine_path, &engine.serialize()).is_err() {
        let _ = fs::remove_file(&engine_path);
    }
    if install(engine, generation) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_FilterEngine_nativeLoad(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    engine_path: JString<'_>,
) -> jboolean {
    let generation = GENERATION.load(Ordering::SeqCst);
    let Some(engine_path) = java_string(&mut env, engine_path) else {
        return JNI_FALSE;
    };
    let Ok(bytes) = fs::read(&engine_path) else {
        return JNI_FALSE;
    };
    let mut engine = Engine::default();
    if engine.deserialize(&bytes).is_err() {
        return JNI_FALSE;
    }
    drop(bytes);
    if install(engine, generation) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_FilterEngine_nativeClear(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) {
    let previous = {
        let Ok(mut current) = filters().write() else {
            return;
        };
        GENERATION.fetch_add(1, Ordering::SeqCst);
        current.take()
    };
    drop(previous);
}

#[no_mangle]
pub extern "system" fn Java_io_github_lucaswotta_webmini_FilterEngine_nativeShouldBlock(
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
