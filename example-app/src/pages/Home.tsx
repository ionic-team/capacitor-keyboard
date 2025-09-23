import {
  IonButton,
  IonButtons,
  IonContent,
  IonHeader,
  IonInput,
  IonPage,
  IonMenuButton,
  IonTitle,
  IonToolbar,
  useIonViewDidEnter,
  useIonViewDidLeave,
  IonText,
} from '@ionic/react';
import React, { useState, useRef } from 'react';
import { Keyboard, KeyboardResize, KeyboardStyle } from '@capacitor/keyboard';
import { PluginListenerHandle } from '@capacitor/core';

const KeyboardPage: React.FC = () => {
  let hideHandler: PluginListenerHandle;
  let didHideHandler: PluginListenerHandle;
  let showHandler: PluginListenerHandle;
  let didShowHandler: PluginListenerHandle;

  let isBarShowing = true;
  let scrollEnabled = true;

  const [keyboardWillShowCounter, setKeyboardWillShowCounter] = useState(0);
  const [keyboardDidShowCounter, setKeyboardDidShowCounter] = useState(0);
  const [keyboardWillHideCounter, setKeyboardWillHideCounter] = useState(0);
  const [keyboardDidHideCounter, setKeyboardDidHideCounter] = useState(0);

  const [keyboardHeight, setKeyboardHeight] = useState(0);

  const inputRef = useRef<HTMLIonInputElement>(null);
  const inputRefSecond = useRef<HTMLIonInputElement>(null);

  useIonViewDidEnter(() => {
    setListeners();
  });

  const setListeners = async () => {
    showHandler = await Keyboard.addListener('keyboardWillShow', info => {
      console.log('keyboard show', info);
      setKeyboardWillShowCounter(prev => prev + 1);
      setKeyboardHeight(info.keyboardHeight);
    });
    didShowHandler = await Keyboard.addListener('keyboardDidShow', info => {
      console.log('keyboard did show', info);
      setKeyboardDidShowCounter(prev => prev + 1);
    });
    hideHandler = await Keyboard.addListener('keyboardWillHide', () => {
      console.log('keyboard hide');
      setKeyboardWillHideCounter(prev => prev + 1);
    });
    didHideHandler = await Keyboard.addListener('keyboardDidHide', () => {
      console.log('keyboard did hide');
      setKeyboardDidHideCounter(prev => prev + 1);
    });
  };

  useIonViewDidLeave(() => {
    hideHandler.remove();
    showHandler.remove();
    didHideHandler.remove();
    didShowHandler.remove();
  });

  const hideAfter5Seconds = async () => {

    inputRef.current?.getInputElement().then(el => el.blur());

    setTimeout(() => {
      Keyboard.hide();
      console.log("Executed after 5 seconds");
    }, 5000);
  };

  const resetCounters = async () => {
    setKeyboardWillShowCounter(0)
    setKeyboardDidShowCounter(0)
    setKeyboardWillHideCounter(0)
    setKeyboardDidHideCounter(0)
  }

  const show = async () => {
    Keyboard.show();
  };

  const hide = async () => {
    Keyboard.hide();
  };

  const toggleAccessoryBar = async () => {
    isBarShowing = !isBarShowing;
    Keyboard.setAccessoryBarVisible({
      isVisible: isBarShowing,
    });
  };

  const toggleScroll = async () => {
    scrollEnabled = !scrollEnabled;
    Keyboard.setScroll({
      isDisabled: scrollEnabled,
    });
  };

  const setStyleLight = async () => {
    Keyboard.setStyle({ style: KeyboardStyle.Light });
  };

  const setStyleDark = async () => {
    Keyboard.setStyle({ style: KeyboardStyle.Dark });
  };

  const setStyleDefault = async () => {
    Keyboard.setStyle({ style: KeyboardStyle.Default });
  };

  const setResizeModeNone = async () => {
    Keyboard.setResizeMode({ mode: KeyboardResize.None });
  };

  const setResizeModeBody = async () => {
    Keyboard.setResizeMode({ mode: KeyboardResize.Body });
  };

  const setResizeModeNative = async () => {
    Keyboard.setResizeMode({ mode: KeyboardResize.Native });
  };

  const setResizeModeIonic = async () => {
    Keyboard.setResizeMode({ mode: KeyboardResize.Ionic });
  };

  const getResizeMode = async () => {
    const res = await Keyboard.getResizeMode();
    alert(res.mode);
  };

  return (
    <IonPage>
      <IonHeader>
        <IonToolbar>
          <IonButtons slot="start">
            <IonMenuButton />
          </IonButtons>
          <IonTitle>Keyboard</IonTitle>
        </IonToolbar>
      </IonHeader>
      <IonContent>
        <IonButton expand="block" onClick={show}>
          Show
        </IonButton>
        <IonButton expand="block" onClick={hide}>
          Hide
        </IonButton>
        <IonButton expand="block" onClick={toggleAccessoryBar}>
          Toggle Accessory Bar
        </IonButton>
        <IonButton expand="block" onClick={toggleScroll}>
          Toggle Scroll
        </IonButton>
        <IonButton expand="block" onClick={setStyleLight}>
          set Style Light
        </IonButton>
        <IonButton expand="block" onClick={setStyleDark}>
          set Style Dark
        </IonButton>
        <IonButton expand="block" onClick={setStyleDefault}>
          set Style Default
        </IonButton>
        <IonButton expand="block" onClick={setResizeModeNone}>
          set Resize Mode None
        </IonButton>
        <IonButton expand="block" onClick={setResizeModeBody}>
          set Resize Mode Body
        </IonButton>
        <IonButton expand="block" onClick={setResizeModeNative}>
          set Resize Mode Native
        </IonButton>
        <IonButton expand="block" onClick={setResizeModeIonic}>
          set Resize Mode Ionic
        </IonButton>
        <IonButton expand="block" onClick={getResizeMode}>
          get Resize Mode
        </IonButton>
        <IonButton expand="block" onClick={hideAfter5Seconds}>
          Hide After 5 Seconds
        </IonButton>
        <IonInput ref={inputRef} placeholder="Enter Input First"></IonInput>
        <IonInput ref={inputRefSecond} placeholder="Enter Input Second"></IonInput>
        <IonText>
          <h2>keyboardWillShow: {keyboardWillShowCounter}</h2>
        </IonText>
        <IonText>
          <h2>keyboardDidShow: {keyboardDidShowCounter}</h2>
        </IonText>
        <IonText>
          <h2>keyboardWillHide: {keyboardWillHideCounter}</h2>
        </IonText>
        <IonText>
          <h2>keyboardDidHide: {keyboardDidHideCounter}</h2>
        </IonText>
        <IonText>
          <h2>Keyboard Height: {keyboardHeight}</h2>
        </IonText>
        <IonButton expand="block" onClick={resetCounters}>
          Reset Counters
        </IonButton>
      </IonContent>
    </IonPage>
  );
};

export default KeyboardPage;
