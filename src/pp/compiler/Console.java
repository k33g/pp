package pp.compiler;

// TO DO
// synchroniser l'affichage

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/*-------------------------\
!                          !
!  La console PP           !
!  @author Archibald       !
!                          !
\-------------------------*/
public class Console extends View  implements GestureDetector.OnGestureListener 
{

	Paint backPaint;
	static final int QUEUE_SIZE = 16; // size of the input queue
	
	private int visibleWidth=0; 
	private int visibleHeight=0;
	private int topVisible=0;
	private int leftVisible=0;
	private Rect visibleRect;
	private int newHeight;
	private int newWidth;
	private int newTop;
	private int newLeft;
	
	int xCursor;   // corsor x position
	int yCursor;   // la position verticale est utilis�e pour le mouvement de la fen�tre
	                       // dans ConsoleView
	int NbLines;           // nombre de lignes affich�es � l'�cran
	int NbRows;     // nombre de colonnes affich�es � l'�cran
	int firstLine;  // num�ro de la premi�re ligne affich�e
	  // est toujours compris entre 0 et NB_LINES - NbLines
	private   int firstIndex; // index dans le tableau de la premi�re ligne affich�e � l'�cran
	  // afin d'�conomiser des recopies, l'�cran est un anneau
	private  char[] ScreenBuffer;
	private  int screenSize; // taille de l'�cran, calcul� lors de la mise � jour de la taille
	   // equals NB_LINES * Nbrows
	final  int NB_LINES = 50; // nombre total de lignes de l'�cran, y compris celles non affich�es
	private boolean cursorBlink;  // flag d'affichage du curseur alternativement mis � vrai ou faux
	// pour d�finir un curseur clignotant
	// �l�ments n�cessaires � l'affichage
	boolean cursorVisible;

	boolean fullscreen=false;
	
	int ForeColor;  // couleur des caract�res
	int BackColor;  // couleur du fond
	final public int CursorPaint=0xff808080;  // cursor color grey 
	private  final char[] THE_CHAR = {'M'};   // a character to get width of characters
	Paint Pt;
	int CharHeight;
	int CharAscent;
	int CharDescent;
	int CharWidth;
	
	Activity activity;

	private Queue InputBuffer;
	
	void inputChar(char c)
	{ 
		InputBuffer.putByte((byte)c);
	}
	
	char getChar()
	{
		return (char)InputBuffer.getByte();
	}
	
	// set cursor position at coordinates (x,y)
	//-----------------------------------------
	void setCursor(int x, int y)
	{
		int index,i;
		yCursor=y;
		index=firstIndex+yCursor*NbRows;
		if (index>=screenSize) index-=screenSize; 
		i=index;
		// search for the end of line
		while (i-index<=x)
		{
			if (ScreenBuffer[i]<' ') break;
			i++;
		}
		// replace null characters by regular spaces in order to display 
		while (i-index<x)
		{
			if (ScreenBuffer[i]<' ') ScreenBuffer[i]=' ';
			i++;
		}
		xCursor=x;
	}
	
	// emit a character on the console
	//---------------------------------
	public  void emitChar(char c)
	{
		int index=firstIndex+yCursor*NbRows+xCursor;  // calcul de l'index o� placer ce caract�re
		if (index>=screenSize) index-=screenSize;     // modulo la taille de l'�cran
		switch(c)
		{
			case '\n':  // fin de ligne
				ScreenBuffer[index]='\n';
				nextLine();  
				// PPS.main();
				break;
			case '\177':  // del
			case 8:
				if (xCursor>0)
				{// si il y a un caract�re � effacer sur la ligne
					xCursor--;
					ScreenBuffer[index-1]='\0';
				}
				else
				{ // sinon, voir si c'est une ligne qui a �t� coup�e
					if (yCursor>0)
					{
						if (ScreenBuffer[index-1]>=' ')  // le caract�re pr�c�dent est-il affichable
						{
							ScreenBuffer[index-1]='\0';
							xCursor=NbRows-1;
							yCursor--;
							makeCursorVisible();
						}	
					}
				}
				break;
			default:
				makeCursorVisible();
				if (c>=' ')
				{
					ScreenBuffer[index]=c;
					xCursor++;
					if (xCursor>=NbRows)
					{
						nextLine();
					}
				}
		}
		postInvalidate();
	}
	
	public void emitString(String msg)
	{
		for(int i=0;i<msg.length();i++)
			emitChar(msg.charAt(i));
	}
	
	private  void nextLine()
	{
		xCursor=0;
		yCursor++;
		if (yCursor>=NB_LINES)
		{ 
			yCursor=NB_LINES-1;
			for(int i=0;i<NbRows;i++) ScreenBuffer[firstIndex+i]='\0';  // effacer la ligne
			firstIndex+=NbRows;
			if (firstIndex>=screenSize) firstIndex=0;
		}
		makeCursorVisible();
	}


	// � cause d'un truc bizare dans getWindowVisibleDisplayFrame qui ne donne pas toujours
	// la bonne valeur 
	// au premier lancement, il donne une valeur top=25 qui laisse un blanc au dessus de la console
	// mais apr�s getWindowsVisibleDisplayFrame donne la bonne valeur
	// �a ne marche qu'en mode fullscreen
    private void getNewDim()
    {
		getWindowVisibleDisplayFrame(visibleRect);
		if (fullscreen)
		{
			newTop=Math.min(getTop(), visibleRect.top);
		    newHeight=visibleRect.bottom-newTop;
		}
		else
		{
			newTop=getTop();
			newHeight=visibleRect.height();
		}
		newWidth=visibleRect.width();
		newLeft=visibleRect.left;
    }
    

    // common initialization routine for all constructors
	private void commonInit()
	{
		backPaint=new Paint();
		visibleRect= new Rect();
		GD=new GestureDetector(this);
		InputBuffer = new Queue(QUEUE_SIZE);
	}
	
	public Console(Context context, AttributeSet attrs) 
	{   // Constructor that is called when inflating a view from XML
        super(context, attrs, 0);
        commonInit();
    }
	
	public Console(Context context, AttributeSet attrs, int defStyles) 
	{   // Constructor that is called when inflating a view from XML
        super(context, attrs, defStyles);
        commonInit();
    }
	
	// initialisation
	// constructor
	public void InitConsole (Activity a, float fontSize, int foreColor, int backColor)
	{
	
		activity=a;
		this.ForeColor=foreColor;
		this.BackColor=backColor;
		Pt = new Paint(); 
		
        Pt.setTypeface(Typeface.MONOSPACE);   
        Pt.setAntiAlias(true);
        Pt.setTextSize(fontSize);

        CharHeight = (int) Math.ceil(Pt.getFontSpacing());
        CharAscent = (int) Math.ceil(Pt.ascent());
        CharDescent = CharHeight + CharAscent;
        CharWidth = (int) Pt.measureText(THE_CHAR, 0, 1);
 
		// initialisation des variables de classe
		xCursor=0;  // position initiale du curseur
		yCursor=0;

		firstLine=0; 
		firstIndex=0;   

		// l'allocation de la m�moire �cran est faite par updateSize
		// pour un nombre de lignes �gal � NB_LINES
		ScreenBuffer=null;
		cursorBlink=true;
		cursorVisible=true;
		//echo=true; //false;
		//batchmode=false;
		backPaint.setColor(BackColor);
	}
	

	void ClearScreen()
	{
		int i;
		for (i=0;i<screenSize;i++) ScreenBuffer[i]='\0'; // erase screen
		xCursor=0;
		yCursor=0;
		firstLine=0;
		firstIndex=0;
		postInvalidate();
	}
	
	// mise � jour de la taille
	public boolean updateSize()
    {
		boolean invalid=false;

        getNewDim();
		
        if ( (newWidth != visibleWidth) || (newHeight != visibleHeight) ) 
        {
            visibleWidth = newWidth;
            visibleHeight = newHeight;
            tUpdateSize(visibleWidth, visibleHeight);
            invalid=true;
        }
        if ((newLeft!=leftVisible)||(newTop!=topVisible))
        {
        	leftVisible=newLeft;
        	topVisible=newTop;
        	invalid=true;
        }

        if (invalid) postInvalidate();
        return invalid;

    }

	// chage la position de la premi�re ligne pour que le curseur soit visible
	void makeCursorVisible()
	{
		if (yCursor-firstLine>=NbLines) 
		{
			firstLine=yCursor-NbLines+1;
		}
		else if (yCursor<firstLine)
		{
			firstLine=yCursor;//Math.max(0, yCursor-NbLines+1);
		}
		
	}

	
	private int trueIndex(int i, int first, int max)
	{
		i+=first;
		if (i>max) i-=max;
		return i;
	}
	
	// mise � jour de la taille
	// rend true si la taille a effectivement chang�
	//-------------------------
	public boolean tUpdateSize(int newWidth, int newHeight)
	{
		// Calcul du nombre de lignes et de colonnes
		int newNbRows = newWidth/CharWidth;
		int i, j;
		int newFirstIndex=0;
		int newNbLines = newHeight/CharHeight;
		boolean value= newNbRows!=NbRows || newNbLines!=NbRows;
		NbLines=newNbLines;
		if (newNbRows!=NbRows)
		{  // si le nombre de colonnes � chang�, r�initialiser
			// et affecter un nouveau buffer d'�cran
			int newScreenSize=NB_LINES*newNbRows;
			char newScreenBuffer[]=new char[newScreenSize];
			// toujours initialiser avec des caract�res nuls
			for (i=0;i<newScreenSize;i++)
			{
				newScreenBuffer[i]='\0';
			}
			if (ScreenBuffer!=null)
			{ // les autres fois, recopier les lignes de l'�cran
				i=0;     // index virtuel 0.. max
				int nextj=0;
				int endi=yCursor*NbRows+xCursor;  // fin des donn�es de l'�cran
				char c;
				do
				{
					
					j=nextj;
					do  // recopie d'une ancienne ligne
					{
						c=ScreenBuffer[trueIndex(i++, firstIndex, screenSize)];
						newScreenBuffer[trueIndex(j++, newFirstIndex, newScreenSize)]=c;
						newFirstIndex=Math.max(0,j/newNbRows-NB_LINES+1)*newNbRows;
					}
					while (c>=' ');
					i--;
					j--;
					// positionnement sur la prochaine ligne
					i+=(NbRows-i%NbRows);
					nextj=j+(newNbRows-j%newNbRows);
				} 
				while (i<endi);
				if (c=='\n') j=nextj;
				yCursor=j/newNbRows; // positionnement du curseur � la fin
				xCursor=j%newNbRows;

			}
			NbRows=newNbRows;
			screenSize=newScreenSize;
			ScreenBuffer=newScreenBuffer;
			firstIndex=newFirstIndex;
		}
		makeCursorVisible();
		return value;
	}

	
    @Override
    public boolean onCheckIsTextEditor()
    {
        return true;
    }
    

	@Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) 
    {
        outAttrs.inputType = EditorInfo.TYPE_NULL;  //TYPE_CLASS_TEXT; // l'alternative est TYPE_NULL

        return new BaseInputConnection(this, false)
        {
        	@Override
            public boolean commitText(CharSequence text, int newCursorPosition) 
            {
                int n = text.length();
                for(int i = 0; i < n; i++) 
                {
                    inputChar(text.charAt(i));
                }
                //invalidate();
                return true;
            }
        	

            @Override
            public boolean performEditorAction(int actionCode) 
            {
                if(actionCode == EditorInfo.IME_ACTION_UNSPECIFIED)
                {
                    // The "return" key has been pressed on the IME.
                    inputChar('\n');
                    //invalidate();
                    return true;
                }
                return false;
            }

            private final String KEYCODE_CHARS =
                "\000\000\000\000\000\000\000" + "0123456789*#"
                + "\000\000\000\000\000\000\000\000\000\000"
                + "abcdefghijklmnopqrstuvwxyz,."
                + "\000\000\000\000"
                + "\011 "   // tab, space
                + "\000\000\000" // sym .. envelope
                + "\015\177" // enter, del
                + "`-=[]\\;'/@"
                + "\000\000\000"
                + "+";

            @Override
            public boolean sendKeyEvent(KeyEvent event) 
            {
                if (event.getAction() == KeyEvent.ACTION_DOWN) 
                {
                    // Some keys are sent here rather than to commitText.
                    // In particular, del and the digit keys are sent here.
                    // (And I have reports that the HTC Magic also sends Return here.)
                    // As a bit of defensive programming, handle every
                    // key with an ASCII meaning.
                    int keyCode = event.getKeyCode();
                    if (keyCode >= 0 && keyCode < KEYCODE_CHARS.length()) 
                    {
                        char c = KEYCODE_CHARS.charAt(keyCode);  // traduction ascii
                        if (c >= 32) 
                        {  // envoie du caract�re au terminal
                            inputChar(c);
                            //invalidate();
                        } 
                    }
                }
                return true;
            }
            
            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition)
            {
                return true;
            }
            
            @Override
            public boolean setSelection(int start, int end) 
            {
                return true;
            }
            
        };
    }
  
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) 
	{
		if (event.isSystem()) 
		{
	         // Don't intercept the system keys
	         return super.onKeyDown(keyCode, event);
	    }
		if (keyCode==KeyEvent.KEYCODE_DEL)
		{
			inputChar((char)8);
			return true;
		}
        // Translate the keyCode into an ASCII character.
		char c=(char)event.getUnicodeChar(); //event.getDisplayLabel(); //event.getMatch(CHARS);
		if (c!='\0')
		{
			inputChar(c);
            return true;
		}
		return false;
    }
	
	@Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) 
    {  // cette m�thode est appel�e lors d'un changement d'orientation
        updateSize();
    }
    


	private void DrawCursor(Canvas canvas, int x, int y)
	{
		if (cursorBlink)
		{
			// dessin du fond du curseur
			Pt.setColor(CursorPaint);
			canvas.drawRect(x,y-1/*+CharAscent*/, x+CharWidth, y+CharDescent, Pt);
		}
		
	}
	
	// affichage d'une ligne � l'�cran
	private	void RenderChars(Canvas canvas, int x, int y, char[]text, int start, int count)
	{

		Pt.setColor(ForeColor);
		canvas.drawText(text, start, count, x, y, Pt);	
	}
	
	// proc�dure de dessin en (x,y) de l'�cran de la console
	//-----------------------------------------------------
	void tDraw(Canvas canvas, int x, int y)
	{
		int index=firstIndex+firstLine*NbRows;
		if (index>=screenSize) index-=screenSize;
		y-=CharAscent;
		if (cursorVisible)
		{
			DrawCursor(canvas, x+xCursor*CharWidth, y+(yCursor-firstLine)*CharHeight);
		}
		for (int i=0;i<NbLines;i++) // affichage de toutes les lignes
		{
			if (i>yCursor-firstLine) break;
			// recherche d'une fin de ligne (tant qu'il y a des caract�res affichables,c-�-d >=' '
			int count=0;
			while ((count<NbRows)&&(ScreenBuffer[count+index]>=' ')) count++;
			RenderChars(canvas,
					       x,         // x position
					       y,         // y position
					       ScreenBuffer,
					       index,
					       count); 
			y+=CharHeight;
			index+=NbRows;
			if (index>=screenSize) index=0;
		}
	}
	
	@Override
    protected void onDraw(Canvas canvas) 
	{
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(leftVisible, topVisible, w, h, backPaint);
        tDraw(canvas,leftVisible,topVisible);
    }

	@Override public boolean onTouchEvent(MotionEvent ev) 
    {
       return GD.onTouchEvent(ev);
    }

	// m�thodes de l'interface GestureDector
	//--------------------------------------
	private float scrollRemainder;
	//private int topLine;
	private GestureDetector GD;
	
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY)
    {
        distanceY += scrollRemainder;
        int deltaRows = (int) (distanceY / CharHeight);
        scrollRemainder = distanceY - deltaRows * CharHeight;
        firstLine=Math.max(0,Math.min(firstLine+deltaRows, yCursor));
        invalidate();

        return true;
   }
    
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) 
    {
        // TODO: add animation man's (non animated) fling
        scrollRemainder = 0.0f;
        onScroll(e1, e2, /* 2 * */ velocityX, -/*2 * */ velocityY);
        return true;
    }
    
    public void onShowPress(MotionEvent e) 
    {
    }
    
    public boolean onSingleTapUp(MotionEvent e) 
    {
    	doShowSoftKeyboard();
        return true;
    }

    public boolean onJumpTapDown(MotionEvent e1, MotionEvent e2)
    {
        // Scroll to bottom
        firstLine = 0;
        invalidate();
        return true;
     } 
   
    public boolean onDown(MotionEvent e) 
    { 
        scrollRemainder = 0.0f;
        return true;
    }
    
    public void onLongPress(MotionEvent e)
    {
        showContextMenu();
    }
   
    public void onSingleTapConfirmed(MotionEvent e)
    {
    }
 
    public boolean onJumpTapUp(MotionEvent e1, MotionEvent e2) 
    {
        // Scroll to top
        invalidate();
        return true;
    }   
    
// fin de gesture detector  
    /**
     * Used to poll if the view has changed size. Wish there was a better way to do this.
     */
	// cette commande est utilis�e pour savoir si la Vue a chang� de taille
	// cette m�thode est r�cup�r�e du terminal jackpal (Jackpal la met dans la View, ici,
	// le Runnable est mis dans l'activit�.
	// apr�s plusieurs essais infructueux pour mettre � jour la taille apr�s l'introduction ou le
	// retrait du clavier soft
	// surcharger onSizeChanged ne marche pas
	// calculer la taille dans onResume ne marche pas non plus
    Runnable checkSize = new Runnable() 
    {
        public void run() // m�thode abstraite
        {
            if (updateSize())
            {
            	invalidate();
            }
            //else
            {
            	myHandler.postDelayed(this,1000);
            }
        }
    };

    Runnable Blink= new Runnable()
    {   
    	public void run()
    	{
    		cursorBlink=!cursorBlink;
    		invalidate();
    		myHandler.postDelayed(this, 300);  // d�lai du curseur
    	}
    };

	 /**
     * Our message handler class. Implements a periodic callback.
     */
    final Handler myHandler = new Handler();


    void onPause()
    {
        myHandler.removeCallbacks(checkSize);  
        myHandler.removeCallbacks(Blink);

    }
    void onResume()
    {
    	myHandler.postDelayed(checkSize,1000);
        myHandler.postDelayed(Blink,500);
        updateSize();
    }

    private void doShowSoftKeyboard()
    {
        InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(this,0);

    }

}


// input queue
//------------
class Queue
{
	private byte Data[];  // array that memorize typed characters
	private int Head;
	private int Tail;
	
	public Queue(int size)
	{
		Data=new byte[size];
		Head=0;  // where to read the data
		Tail=0;  // where to write the data
	}
	
	public synchronized byte getByte()
	{
		while (Head==Tail)  // no data to read
		{
			try
			{
				wait();
			}
			catch (InterruptedException ie){}
		}
		byte b=Data[Head];
		Head++;
		if (Head>=Data.length) Head=0;
		return b;
	}
	
	public synchronized void putByte(byte b)
	{
		Data[Tail]=b;
		Tail++;
		if (Tail>=Data.length) Tail=0;
		if (Head==Tail)
		{   // throw the unread character
			// notify the overflow
			Head++;
			if (Head>=Data.length) Head=0;
		}
		notify();
	}
	/*
	public synchronized  void eraseByte()
	{
		if (Tail!=Head)
		{
			Tail--;
			if (Tail<0) Tail=0;
		}
		notify();
	}

	public synchronized void Flush()
	{
		Tail=Head;
		notify();
	}
	*/
}





